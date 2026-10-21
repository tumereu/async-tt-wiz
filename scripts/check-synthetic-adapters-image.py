#!/usr/bin/env python3
"""Local adapter container qualification with exact-owned cleanup, no published ports."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent


def docker(*args, timeout=120):
    return subprocess.check_output(["docker", *args], cwd=ROOT, stderr=subprocess.STDOUT, timeout=timeout, text=True).strip()


def inventory(kind):
    return set(docker(kind, "ls", "-q").splitlines()) if kind != "container" else set(docker("ps", "-aq").splitlines())


def main():
    manifest = json.loads((ROOT / "e2e/build/adapters/image.json").read_text())
    for relative, expected in manifest["inputs"].items():
        assert hashlib.sha256((ROOT / relative).read_bytes()).hexdigest() == expected, relative
    image = manifest["imageId"]
    prefix = "atw-adapters-" + uuid.uuid4().hex[:12]
    before = {k: inventory(k) for k in ("container", "volume", "network")}
    volume, server, suite = prefix + "-data", prefix + "-server", prefix + "-suite"
    owned = []
    token = "d3" * 32
    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix=prefix, dir="/tmp/opencode") as temp:
        secret = Path(temp) / "token"
        secret.write_text(token + "\n")
        secret.chmod(0o444)
        try:
            # Execute all socket/failure/isolation regressions against the installed
            # image payload, nonroot and with no external network at all.
            command = ["docker", "run", "--name", suite, "--label", "org.asyncttwiz.test-owner=" + prefix,
                       "--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                       "--tmpfs", "/tmp/opencode:rw,mode=1777", "--mount",
                       f"type=bind,src={ROOT / 'tests/test_synthetic_adapters.py'},dst=/checks/test_synthetic_adapters.py,readonly",
                       "--mount", f"type=bind,src={ROOT / 'e2e/adapters/admin.py'},dst=/checks/admin.py,readonly",
                       "-e", "ADAPTER_SCRIPT=/opt/adapters/adapter.py", "-e", "ADAPTER_FIXTURES=/opt/adapters/catalogs",
                       "-e", "ADAPTER_ADMIN=/checks/admin.py",
                       "--entrypoint", "python3", image, "-m", "unittest", "discover", "-s", "/checks", "-v"]
            print("COMMAND", json.dumps(command), flush=True)
            owned.append(suite)
            result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
            print(result.stdout, flush=True)
            assert result.returncode == 0
            docker("volume", "create", "--label", "org.asyncttwiz.test-owner=" + prefix, volume)
            # Initialize only this new disposable volume; no host chown or broad cleanup.
            docker("run", "--rm", "--network=none", "--read-only", "--user", "0:0", "--mount",
                   f"type=volume,src={volume},dst=/data", "--entrypoint", "python3", image, "-c",
                   "import os; os.chown('/data',10001,10001); os.chmod('/data',0o700)")
            owned.append(server)
            docker("run", "-d", "--name", server, "--label", "org.asyncttwiz.test-owner=" + prefix,
                   "--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                   "--mount", f"type=volume,src={volume},dst=/data", "--mount",
                   f"type=bind,src={secret},dst=/run/harness-token,readonly", image,
                   "--origin", "http://device-source.invalid:18080", "--state-dir", "/data",
                   "--instance", prefix, "--token-file", "/run/harness-token")

            def execute(code):
                return docker("exec", server, "python3", "-c", code)

            prelude = "import json,http.client; c=http.client.HTTPConnection('127.0.0.1',8081,timeout=2); "
            auth = repr({"Authorization": "Bearer " + token})

            def request(path, value=None):
                code = prelude + f"c.request({'GET' if value is None else 'POST'!r},{path!r},body={None if value is None else json.dumps(value)!r},headers={auth}); r=c.getresponse(); print(r.status); print(r.read().decode())"
                text = execute(code).split("\n", 1)
                return int(text[0]), json.loads(text[1])

            def wait_ready():
                until = time.monotonic() + 15
                while time.monotonic() < until:
                    try:
                        if request("/health/ready")[0] == 200:
                            return
                    except (subprocess.CalledProcessError, ValueError):
                        pass
                    time.sleep(.05)
                raise AssertionError("image readiness deadline")

            wait_ready()
            config = json.loads(docker("inspect", server))[0]
            assert config["HostConfig"]["NetworkMode"] == "none"
            assert config["HostConfig"]["ReadonlyRootfs"]
            assert config["Config"]["User"] == "10001:10001"
            assert execute("import os; print(os.getpid()); print(os.readlink('/proc/1/exe')); print(open('/proc/1/cmdline','rb').read().split(bytes([0]))[1].decode())").splitlines()[2] == "/opt/adapters/adapter.py"
            self_state = request("/admin/state")[1]
            assert self_state["messageCount"] == 0 and self_state["generation"] == 0
            request("/admin/control", {"instance": prefix, "versions": {"prism": 2, "quilt": 1}})
            execute("import smtplib; s=smtplib.SMTP('127.0.0.1',2525,timeout=2); s.sendmail('synthetic@example.invalid','never-forward@external.invalid',b'Subject: synthetic\\r\\n\\r\\ncontainer-body-sentinel\\r\\n'); s.quit()")
            deliveries = request("/admin/messages")[1]
            assert len(deliveries) == 1
            served = request("/admin/inventory")[1]
            print("SERVED-COPY", json.dumps(served, sort_keys=True), flush=True)
            stop = time.monotonic()
            docker("stop", "-t", "5", server)
            elapsed = time.monotonic() - stop
            info = json.loads(docker("inspect", server))[0]
            assert info["State"]["ExitCode"] == 0 and elapsed < 5
            docker("start", server)
            wait_ready()
            assert request("/admin/messages")[1] == deliveries
            assert request("/admin/state")[1]["versions"] == {"prism": 2, "quilt": 1}
            for _ in range(2):
                status, value = request("/admin/reset", {"instance": prefix})
                assert status == 200 and value["messageCount"] == 0
                assert value["versions"] == {"prism": 1, "quilt": 1}
                wait_ready()
            logs = docker("logs", server)
            assert "container-body-sentinel" not in logs and token not in logs and "external.invalid" not in logs
            print("PASS real nonroot/read-only/network-none PID1 image, no external forwarding path; empty baseline, retained source/delivery restart, two resets, safe logs; SIGTERM seconds", round(elapsed, 3), flush=True)
        finally:
            # Capture safe logs BEFORE targeted cleanup, also on failure.
            for name in owned:
                try:
                    print("OWNED-LOG", name, docker("logs", name), flush=True)
                except subprocess.CalledProcessError:
                    pass
            for name in reversed(owned):
                inspect = subprocess.run(["docker", "inspect", name], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                if inspect.returncode == 0:
                    value = json.loads(inspect.stdout)[0]
                    assert value["Config"]["Labels"]["org.asyncttwiz.test-owner"] == prefix
                    docker("rm", "-f", name)
            if volume in inventory("volume"):
                info = json.loads(docker("volume", "inspect", volume))[0]
                assert info["Labels"]["org.asyncttwiz.test-owner"] == prefix
                docker("volume", "rm", volume)
            after = {k: inventory(k) for k in before}
            assert before == after, {k: before[k] ^ after[k] for k in before}
            print("PASS exact owned container/volume cleanup, no published ports/networks; pre-existing inventory unchanged", flush=True)
    print("PASS image identity", image, "elapsed seconds", round(time.monotonic() - started, 3), flush=True)


if __name__ == "__main__":
    main()
