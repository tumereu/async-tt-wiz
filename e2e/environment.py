"""One explicitly owned synthetic Compose instance; Linux local Docker only.

Fresh provision never reuses a directory or resource. Restart never resets data.
Explicit reset replaces only this instance's quiesced stores. No action API.
Only public backend and
source listeners get loopback forwarding; all container networks are internal.
"""
import argparse
import base64
import contextlib
import fcntl
import hashlib
import http.client
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import signal
import socket
import subprocess
import sys
import time
from string import Template

ROOT = Path(__file__).resolve().parents[1]
BASE = Path("/tmp/opencode")
LABEL = "org.asyncttwiz.e2e-owner"
SERVICES = ("database", "adapter-init", "adapters", "migrate", "backend")


class Refusal(Exception):
    """Fixed messages only; subprocess errors may contain secret-bearing inputs."""


def require(condition, message):
    if not condition:
        raise Refusal(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + ".new")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
    temporary.chmod(0o600)
    temporary.replace(path)


def command(*args, timeout=90):
    result = subprocess.run(args, cwd=ROOT, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=timeout)
    require(result.returncode == 0, "Owned environment command failed; capture diagnostics before cleanup")
    return result.stdout.strip()


def docker(*args, timeout=90):
    return command("docker", *args, timeout=timeout)


def local_daemon():
    # A switched Docker context must not turn test setup into remote deployment.
    endpoint = os.environ.get("DOCKER_HOST")
    if os.environ.get("DOCKER_CONTEXT") or not endpoint:
        endpoint = docker("context", "inspect", "--format", "{{.Endpoints.docker.Host}}")
    require(endpoint.startswith("unix:///"), "Only a local Unix-socket Docker daemon is qualified")


def inspect(kind, name):
    result = subprocess.run(["docker", kind, "inspect", name], cwd=ROOT,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=15)
    if result.returncode == 0:
        return json.loads(result.stdout)[0]
    # Distinguish absence from daemon/permission failure before mutation.
    require("no such" in result.stderr.lower() or "not found" in result.stderr.lower(), "Cannot inspect ownership")
    return None


def directory_path(path):
    path = Path(path)
    require(path.is_absolute() and path.parent == BASE and
            re.fullmatch(r"atw-e2e-[a-f0-9]{32}", path.name),
            "Instance must be /tmp/opencode/atw-e2e-<32 lowercase hex digits>")
    require(not path.is_symlink() and path.resolve() == path, "Symlink instance refused")
    return path


def load(directory):
    require(directory.is_dir() and directory.stat().st_uid == os.getuid() and
            directory.stat().st_mode & 0o077 == 0, "Private owned instance directory required")
    for name in ("instance.json", "compose.json", "db-password", "harness-token", "reset-mail-key"):
        path = directory / name
        require(path.is_file() and not path.is_symlink(), "Missing or unsafe instance input")
    value = json.loads((directory / "instance.json").read_text())
    require(value["project"] == directory.name and value["directory"] == str(directory) and
            re.fullmatch(r"[a-f0-9]{64}", value["owner"]) and value["formatVersion"] == 1,
            "Invalid instance identity")
    require(value["composeSha256"] == sha(directory / "compose.json"), "Changed topology snapshot refused")
    for name, expected in value["secretHashes"].items():
        require(name in ("db-password", "harness-token", "reset-mail-key") and sha(directory / name) == expected,
                "Changed secret file refused")
    return value


@contextlib.contextmanager
def locked(directory):
    path = directory / "controller.lock"
    require(not path.is_symlink(), "Unsafe controller lock")
    with path.open("a") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Refusal("Another controller owns this instance") from None
        yield


def compose(value, *args, timeout=90):
    # Ignore global Compose configuration/profiles; all topology is the snapshot.
    env = {k: v for k, v in os.environ.items() if not k.startswith("COMPOSE_")}
    result = subprocess.run(["docker", "compose", "--project-name", value["project"],
                             "--file", value["directory"] + "/compose.json", *args],
                            cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, timeout=timeout)
    log = Path(value["directory"]) / "commands.log"
    with log.open("a") as output:
        output.write(redact(value, json.dumps(list(args)) + "\n" + result.stdout) + "\n")
    require(result.returncode == 0, "Compose failed; capture diagnostics before cleanup")
    return result.stdout.strip()


def owned(value):
    project, owner = value["project"], value["owner"]
    found = {}
    for service in SERVICES:
        name = project + "-" + service
        info = inspect("container", name)
        if info:
            labels = info["Config"].get("Labels") or {}
            require(labels.get(LABEL) == owner and labels.get("com.docker.compose.project") == project and
                    labels.get("com.docker.compose.service") == service, "Foreign container target refused")
            found[service] = info
    expected_ids = {c["Id"] for c in found.values()}
    actual_ids = set(docker("ps", "-aq", "--no-trunc", "--filter", "label=com.docker.compose.project=" + project).splitlines())
    require(expected_ids == actual_ids, "Ambiguous project containers refused")
    for kind, suffixes in (("volume", ("database", "adapters")), ("network", ("scenario",))):
        for suffix in suffixes:
            info = inspect(kind, project + "-" + suffix)
            if info:
                require((info.get("Labels") or {}).get(LABEL) == owner and
                        (info.get("Labels") or {}).get("com.docker.compose.project") == project,
                        "Foreign storage/network target refused")
                if kind == "network":
                    require(info["Internal"] and set(info.get("Containers", {})) <= expected_ids,
                            "Noninternal or shared network refused")
                else:
                    users = set(docker("ps", "-aq", "--no-trunc", "--filter", "volume=" + info["Name"]).splitlines())
                    require(users <= expected_ids, "Shared volume refused")
    # Nothing outside this inventory may be removed by compose down.
    for kind, suffixes in (("volume", ("database", "adapters")), ("network", ("scenario",))):
        names = set(docker(kind, "ls", "--format", "{{.Name}}", "--filter", "label=com.docker.compose.project=" + project).splitlines())
        require(names <= {project + "-" + s for s in suffixes}, "Ambiguous project resources refused")
    return found


def invalidate(value):
    (Path(value["directory"]) / "ready.json").unlink(missing_ok=True)


def reserve(port=0):
    sock = socket.socket()
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("127.0.0.1", port))
        sock.listen(32)
        return sock
    except BaseException:
        sock.close()
        raise


def process_identity(pid):
    try:
        # /proc stat comm can contain spaces/parentheses.
        stat = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
        if stat[0] == "Z":
            return None
        return {"pid": pid, "startTicks": stat[19],
                "cmdline": Path(f"/proc/{pid}/cmdline").read_bytes().hex()}
    except FileNotFoundError:
        return None


def stop_relay(value):
    file = Path(value["directory"]) / "relay.json"
    if not file.exists():
        return
    recorded = json.loads(file.read_text())
    arguments = bytes.fromhex(recorded["cmdline"]).decode().split("\0")
    require(str(ROOT / "e2e/relay.py") in arguments and "--directory" in arguments and
            arguments[arguments.index("--directory") + 1] == value["directory"], "Foreign relay command refused")
    actual = process_identity(recorded["pid"])
    require(actual is None or actual == recorded, "Reused/foreign relay PID refused")
    if actual:
        os.kill(recorded["pid"], signal.SIGTERM)
        until = time.monotonic() + 3
        while process_identity(recorded["pid"]) and time.monotonic() < until:
            time.sleep(.05)
        if process_identity(recorded["pid"]):
            require(process_identity(recorded["pid"]) == recorded, "Relay PID changed")
            os.kill(recorded["pid"], signal.SIGKILL)
            until = time.monotonic() + 3
            while process_identity(recorded["pid"]) and time.monotonic() < until:
                time.sleep(.05)
        require(process_identity(recorded["pid"]) is None, "Relay did not stop")
    file.unlink()


def ip(value, info):
    require(set(info["NetworkSettings"]["Networks"]) == {value["project"] + "-scenario"} and
            not any(info["NetworkSettings"].get("Ports", {}).values()), "Unexpected container route/publication")
    address = info["NetworkSettings"]["Networks"][value["project"] + "-scenario"]["IPAddress"]
    require(ipaddress.ip_address(address).is_private, "Nonprivate bridge address")
    return address


def start_relay(value):
    file = Path(value["directory"]) / "relay.json"
    resources = owned(value)
    backend_ip = ip(value, resources["backend"])
    source_ip = ip(value, resources["adapters"])
    if file.exists():
        recorded = json.loads(file.read_text())
        arguments = bytes.fromhex(recorded["cmdline"]).decode().split("\0")
        if (process_identity(recorded["pid"]) == recorded and
                "--backend-ip" in arguments and "--source-ip" in arguments and
                arguments[arguments.index("--backend-ip") + 1] == backend_ip and
                arguments[arguments.index("--source-ip") + 1] == source_ip):
            return
        stop_relay(value)
    with contextlib.ExitStack() as stack:
        backend = stack.enter_context(reserve(value["ports"]["backend"]))
        source = stack.enter_context(reserve(value["ports"]["source"]))
        log = stack.enter_context((Path(value["directory"]) / "relay.log").open("ab"))
        child = subprocess.Popen([sys.executable, str(ROOT / "e2e/relay.py"), "--directory", value["directory"],
                                  "--backend-fd", str(backend.fileno()), "--source-fd", str(source.fileno()),
                                  "--backend-ip", backend_ip,
                                  "--source-ip", source_ip],
                                 pass_fds=(backend.fileno(), source.fileno()), stdout=log, stderr=subprocess.STDOUT,
                                 start_new_session=True)
        identity = process_identity(child.pid)
        require(identity is not None, "Relay startup failed")
        write_json(file, identity)


def request(address, port, path, token=None, body=None):
    connection = http.client.HTTPConnection(address, port, timeout=2)
    try:
        headers = {"Authorization": "Bearer " + token} if token else {}
        connection.request("GET" if body is None else "POST", path,
                           body=None if body is None else json.dumps(body), headers=headers)
        response = connection.getresponse()
        data = response.read(2 * 1024 * 1024)
        return response.status, data
    finally:
        connection.close()


def admin(value, path, body=None):
    info = owned(value)["adapters"]
    token = (Path(value["directory"]) / "harness-token").read_text().strip()
    status, data = request(ip(value, info), 8081, path, token, body)
    require(status == 200, "Synthetic adapter administration unavailable")
    return json.loads(data)


def database(value, sql):
    return docker("exec", value["project"] + "-database", "psql", "-U", "atw", "-d", "atw",
                  "-AtX", "-v", "ON_ERROR_STOP=1", "-c", sql, timeout=10)


def deadline(check, timeout):
    until = time.monotonic() + timeout
    while True:
        try:
            return check()
        except (Refusal, OSError, ValueError, KeyError, http.client.HTTPException):
            if time.monotonic() >= until:
                raise Refusal("Readiness deadline; no ready handoff issued") from None
            time.sleep(.2)


def wait(value, timeout, *, completing_reset=False):
    invalidate(value)
    require(completing_reset or not (Path(value["directory"]) / "reset-pending.json").exists(),
            "Incomplete reset; explicitly reset before requesting a handoff")

    def check():
        resources = owned(value)
        for service in ("database", "backend", "adapters"):
            require(resources[service]["State"]["Running"], "Required service stopped")
            require(not resources[service]["HostConfig"]["PortBindings"], "Unexpected published port")
        backend_ip = ip(value, resources["backend"])
        for path in ("/health/live", "/health/ready"):
            require(request(backend_ip, 8081, path) == (200, b"OK\n"), "Backend unready")
        require(admin(value, "/health/ready") == {"status": "up"}, "Adapters unready")
        state = admin(value, "/admin/state")
        require(state["instance"] == value["project"], "Adapter owner mismatch")
        epoch = database(value, "SELECT epoch FROM service_writer WHERE singleton")
        require(epoch.isdigit() and int(epoch) > 0, "No acquired writer epoch")
        require(database(value, "SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND granted") == "1",
                "Missing current writer lock")
        start_relay(value)
        require(request("127.0.0.1", value["ports"]["backend"], "/health/ready") == (404, b"Not found\n"),
                "Public backend/health separation failed")
        for dataset in ("prism", "quilt"):
            status, data = request("127.0.0.1", value["ports"]["source"], "/" + dataset + "/Meta.json")
            require(status == 200 and "meta" in json.loads(data), "Source unavailable")
        require(request("127.0.0.1", value["ports"]["source"], "/admin/state")[0] == 404,
                "Public source administration exposed")
        return {"instance": value["project"], "backend": value["backendOrigin"], "source": value["sourceOrigin"],
                "hostBackend": "http://127.0.0.1:" + str(value["ports"]["backend"]),
                "hostSource": "http://127.0.0.1:" + str(value["ports"]["source"]),
                "writerEpoch": int(epoch), "checkedAtUnix": time.time(),
                "scope": "Host infrastructure only. Call wait before setup; not a perpetual health lease or Android acceptance."}

    ready = deadline(check, timeout)
    write_json(Path(value["directory"]) / "ready.json", ready)
    return ready


def redact(value, text):
    for name in ("db-password", "harness-token", "reset-mail-key"):
        file = Path(value["directory"]) / name
        if file.exists():
            text = text.replace(file.read_text().strip(), "[REDACTED]")
    return text


def capture(value):
    resources = owned(value)
    output = {"instance": value["project"], "images": value["images"], "inputs": value["inputs"],
              "composeVersion": docker("compose", "version"), "containers": {},
               "implementedWorkers": ["backend writer ownership/recovery", "encrypted reset-mail outbox/SMTP sender", "synthetic source/mail sink"],
               "absentWorkers": ["game", "game inbox/outbox", "push"]}
    for service, info in resources.items():
        logs = docker("logs", "--tail", "150", info["Id"])
        output["containers"][service] = {"id": info["Id"], "image": info["Image"],
                                          "state": {k: info["State"][k] for k in ("Status", "ExitCode", "OOMKilled")},
                                          "logs": redact(value, logs)}
    for key, action in (("adapterState", lambda: admin(value, "/admin/state")),
                        ("servedInventory", lambda: admin(value, "/admin/inventory")),
                        ("epoch", lambda: database(value, "SELECT epoch FROM service_writer"))):
        try:
            output[key] = action()
        except (Refusal, OSError, KeyError, ValueError, http.client.HTTPException, subprocess.TimeoutExpired):
            output[key] = "unavailable"
    log = Path(value["directory"]) / "commands.log"
    output["commands"] = redact(value, log.read_text() if log.exists() else "")
    destination = Path(value["directory"]) / ("capture-" + secrets.token_hex(8) + ".json")
    write_json(destination, output)
    return destination


def image_inputs():
    server = json.loads((ROOT / "server/build/application-image.json").read_text())
    adapters = json.loads((ROOT / "e2e/build/adapters/image.json").read_text())
    require(server["platform"] == adapters["platform"] == "linux/amd64", "Unqualified image platform")
    require(sha(ROOT / "server/Dockerfile") == server["dockerfileSha256"], "Stale server Dockerfile")
    for name, expected in server["libraries"].items():
        require(sha(ROOT / "server/build/install/server/lib" / name) == expected, "Stale server distribution")
    for name, expected in adapters["inputs"].items():
        require(sha(ROOT / name) == expected, "Stale adapter image inputs")
    postgres = re.search(r"^postgres=(.+)$", (ROOT / "server/database-images.properties").read_text(), re.M)[1]
    images = {"server": server["imageId"], "adapters": adapters["imageId"], "postgres": postgres}
    for image in images.values():
        require(re.fullmatch(r"(?:postgres@)?sha256:[0-9a-f]{64}", image), "Immutable local image required")
        info = json.loads(docker("image", "inspect", image))[0]
        require(info["Os"] == "linux" and info["Architecture"] == "amd64", "Image platform mismatch")
    inputs = {str(p.relative_to(ROOT)): sha(p) for p in
              (ROOT / "e2e/compose.json", ROOT / "e2e/environment.py", ROOT / "e2e/relay.py",
               ROOT / "server/build/application-image.json", ROOT / "e2e/build/adapters/image.json")}
    return images, inputs


def provision(directory, backend_port, source_port, timeout):
    images, inputs = image_inputs()
    # mkdir is deliberately exclusive; no provision-on-existing/reset fallback.
    directory.mkdir(mode=0o700)
    with locked(directory), contextlib.ExitStack() as stack:
        backend = stack.enter_context(reserve(backend_port))
        source = stack.enter_context(reserve(source_port))
        ports = {"backend": backend.getsockname()[1], "source": source.getsockname()[1]}
        value = {"formatVersion": 1, "project": directory.name, "directory": str(directory),
                 "owner": secrets.token_hex(32), "images": images, "inputs": inputs, "ports": ports,
                 "backendOrigin": "http://127.0.0.1:" + str(ports["backend"]),
                 "sourceOrigin": "http://127.0.0.1:" + str(ports["source"])}
        for name in ("db-password", "harness-token", "reset-mail-key"):
            path = directory / name
            path.write_text((base64.b64encode(secrets.token_bytes(32)).decode() if name == "reset-mail-key" else secrets.token_hex(32)) + "\n")
            # Directory is 0700; file mounts must be readable by image UID 10001.
            path.chmod(0o444)
        value["secretHashes"] = {n: sha(directory / n) for n in ("db-password", "harness-token", "reset-mail-key")}
        replacements = {"PROJECT": value["project"], "OWNER": value["owner"], "DIRECTORY": str(directory),
                        "SERVER_IMAGE": images["server"], "ADAPTER_IMAGE": images["adapters"],
                        "POSTGRES_IMAGE": images["postgres"], "SOURCE_ORIGIN": value["sourceOrigin"],
                        "BACKEND_ORIGIN": value["backendOrigin"]}
        topology = json.loads(Template((ROOT / "e2e/compose.json").read_text()).substitute(replacements))
        write_json(directory / "compose.json", topology)
        value["composeSha256"] = sha(directory / "compose.json")
        write_json(directory / "instance.json", value)
        # An existing name, even if it has matching-looking labels, is not fresh.
        require(not owned(value), "Existing container names refused")
        for kind, suffixes in (("volume", ("database", "adapters")), ("network", ("scenario",))):
            for suffix in suffixes:
                require(inspect(kind, value["project"] + "-" + suffix) is None, "Existing resource refused")
        compose(value, "config", "--quiet")
        try:
            fresh_services(value, timeout)
            # Release reservations only immediately before inheriting/rebinding.
            backend.close()
            source.close()
            ready = wait(value, timeout)
            require(admin(value, "/admin/state")["messageCount"] == 0, "Nonempty fresh mailbox")
            return ready
        except BaseException:
            invalidate(value)
            capture(value)
            raise


def fresh_services(value, timeout):
    compose(value, "up", "-d", "--no-build", "--pull", "never", "database")
    deadline(lambda: database(value, "SELECT 1"), timeout)
    compose(value, "up", "--no-build", "--pull", "never", "--exit-code-from", "adapter-init", "adapter-init")
    compose(value, "up", "--no-build", "--pull", "never", "--exit-code-from", "migrate", "migrate")
    require(database(value, "SELECT epoch FROM service_writer WHERE singleton") == "0", "Nonfresh database")
    compose(value, "up", "-d", "--no-build", "--pull", "never", "adapters", "backend")


def empty_baseline(value):
    # Deliberately explicit implemented-store inventory. New migrations/workers
    # must extend this qualification, never silently escape the reset contract.
    require(database(value, "SELECT string_agg(tablename,',' ORDER BY tablename) FROM pg_tables WHERE schemaname='public'")
            == "account,account_definition,account_request,account_session,contact,deck_entry,definition,flyway_schema_history,game,game_definition,game_inbox,game_outbox,game_revision,game_stream,invitation,password_reset_token,player_preparation,request_identity,reset_mail,saved_deck,service_writer", "Unexpected baseline tables")
    require(database(value, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank") == "1\n2\n3\n4\n5\n6\n7\n8", "Unexpected migration baseline")
    mutable = ("account", "account_session", "password_reset_token", "reset_mail", "definition",
               "account_definition", "saved_deck", "deck_entry", "request_identity", "account_request", "contact", "game", "invitation",
               "game_inbox", "game_outbox", "game_revision", "game_stream", "player_preparation", "game_definition")
    for table in mutable:
        require(database(value, "SELECT count(*) FROM " + table) == "0", "Retained private baseline")
    require(database(value, "SELECT count(*) FROM service_writer WHERE singleton AND epoch > 0") == "1", "Missing baseline writer")
    state = admin(value, "/admin/state")
    require(state["instance"] == value["project"] and state["versions"] == {"prism": 1, "quilt": 1} and
            state["generation"] == 0 and state["messageCount"] == 0 and
            state["sourceFault"] == state["mailFault"] == "ok", "Nonempty adapter baseline")
    require(admin(value, "/admin/messages") == [], "Retained baseline deliveries")
    return {"tables": list(mutable) + ["flyway_schema_history", "service_writer"], "migrations": ["1", "2", "3", "4", "5", "6", "7", "8"],
            "accountCount": 0, "sessionCount": 0, "resetTokenCount": 0, "resetMailCount": 0,
            "versions": state["versions"], "messageCount": 0, "faults": "ok"}


def remove_runtime(value):
    resources = owned(value)  # Check every owner/shared-volume guard before stopping.
    old_ids = {s: c["Id"] for s, c in resources.items()}
    stop_relay(value)
    compose(value, "--profile", "setup", "stop", "--timeout", "30")
    for info in owned(value).values():
        require(not info["State"]["Running"] and info["State"]["Pid"] == 0, "Old producer still running")
    # Remove processes and namespaces before deleting any stores. A connected old
    # DB/SMTP socket belongs to one of these dead namespaces, not the new baseline.
    compose(value, "--profile", "setup", "down", "--timeout", "30")
    require(not owned(value), "Old containers remain")
    for identity in old_ids.values():
        require(inspect("container", identity) is None, "Old producer identity remains")
    require(inspect("network", value["project"] + "-scenario") is None, "Old namespace remains")
    for suffix in ("database", "adapters"):
        name = value["project"] + "-" + suffix
        if inspect("volume", name):
            require(not docker("ps", "-aq", "--filter", "volume=" + name), "Old store still connected")
            docker("volume", "rm", name)
        require(inspect("volume", name) is None, "Old store remains")
    return old_ids


def reset(value, timeout):
    invalidate(value)
    owned(value)
    directory = Path(value["directory"])
    pending = directory / "reset-pending.json"
    write_json(pending, {"instance": value["project"], "phase": "quiescing"})
    try:
        capture(value)  # Preserve the failed/dirty scenario before its explicit reset.
        old_ids = remove_runtime(value)
        write_json(pending, {"instance": value["project"], "phase": "rebuilding", "removedContainers": old_ids})
        fresh_services(value, timeout)
        baseline = deadline(lambda: empty_baseline(value), timeout)
        ready = wait(value, timeout, completing_reset=True)
        write_json(directory / "reset-completed.json", {"instance": value["project"], "removedContainers": old_ids,
                   "baseline": baseline, "checkedAtUnix": ready["checkedAtUnix"]})
        pending.unlink()
        return ready
    except BaseException:
        invalidate(value)
        stop_relay(value)
        capture(value)
        raise


def restart(value, service, timeout):
    invalidate(value)
    require(not (Path(value["directory"]) / "reset-pending.json").exists(), "Incomplete reset; restart is not repair")
    owned(value)
    stop_relay(value)
    targets = [service] if service != "all" else ["backend", "adapters", "database"]
    compose(value, "stop", *targets)
    # Reuse containers, volumes and addresses, with no migration/reset/recreate.
    for target in reversed(targets):
        compose(value, "start", target)
    return wait(value, timeout)


def cleanup(value):
    invalidate(value)
    owned(value)  # Refuse all ambiguous/shared resources BEFORE any shutdown.
    capture(value)  # Failure evidence precedes deletion, even on an unready system.
    remove_runtime(value)
    require(not owned(value), "Owned containers remain after cleanup")
    for kind, suffixes in (("volume", ("database", "adapters")), ("network", ("scenario",))):
        for suffix in suffixes:
            require(inspect(kind, value["project"] + "-" + suffix) is None, "Owned resource remains")
    for port in value["ports"].values():
        with reserve(port):
            pass
    for name in ("db-password", "harness-token", "reset-mail-key"):
        (Path(value["directory"]) / name).unlink()
    write_json(Path(value["directory"]) / "cleaned.json", {"instance": value["project"], "ownedResourcesRemoved": True,
                                                            "portsRebindable": True, "temporarySecretsRemoved": True})
    return {"instance": value["project"], "cleaned": True}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", required=True)
    parser.add_argument("--timeout", type=int, default=60, choices=range(1, 121), metavar="1..120")
    commands = parser.add_subparsers(dest="operation", required=True)
    provision_parser = commands.add_parser("provision")
    provision_parser.add_argument("--backend-port", type=int, default=0)
    provision_parser.add_argument("--source-port", type=int, default=0)
    commands.add_parser("wait")
    commands.add_parser("reset")
    restart_parser = commands.add_parser("restart")
    restart_parser.add_argument("--service", choices=["all", "backend", "database", "adapters"], default="all")
    commands.add_parser("capture")
    commands.add_parser("cleanup")
    args = parser.parse_args(argv)
    try:
        local_daemon()
        directory = directory_path(args.directory)
        if args.operation == "provision":
            result = provision(directory, args.backend_port, args.source_port, args.timeout)
        else:
            value = load(directory)
            with locked(directory):
                if args.operation != "capture":
                    invalidate(value)
                owned(value)
                if args.operation == "wait":
                    result = wait(value, args.timeout)
                elif args.operation == "restart":
                    result = restart(value, args.service, args.timeout)
                elif args.operation == "reset":
                    result = reset(value, args.timeout)
                elif args.operation == "capture":
                    result = {"capture": str(capture(value))}
                else:
                    result = cleanup(value)
        print(json.dumps(result, sort_keys=True), flush=True)
    except (Refusal, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        # No exception/input echo: mounted secrets/raw Docker output stay private.
        print("Environment refused: " + (str(error) if isinstance(error, Refusal) else "invalid input or unavailable local tooling"), file=sys.stderr)
        raise SystemExit(1) from None
