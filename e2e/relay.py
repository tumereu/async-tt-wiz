#!/usr/bin/env python3
"""Owned loopback TCP forwarding into an internal bridge; never log payloads.

Only the two already-bound public/source sockets are inherited from the lifecycle
controller. No Docker API, administration listener, external route or TLS bypass.
"""
import argparse
import concurrent.futures
import select
import signal
import socket
import time


def forward(client, target):
    with client:
        try:
            with socket.create_connection(target, timeout=2) as upstream:
                client.settimeout(2)
                upstream.settimeout(2)
                until = time.monotonic() + 120
                while time.monotonic() < until:
                    ready, _, _ = select.select([client, upstream], [], [], .25)
                    for source in ready:
                        data = source.recv(65536)
                        destination = upstream if source is client else client
                        if not data:
                            return
                        destination.sendall(data)
        except OSError:
            pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", required=True)
    parser.add_argument("--backend-fd", type=int, required=True)
    parser.add_argument("--source-fd", type=int, required=True)
    parser.add_argument("--backend-ip", required=True)
    parser.add_argument("--source-ip", required=True)
    args = parser.parse_args()
    listeners = {socket.socket(fileno=args.backend_fd): (args.backend_ip, 8080),
                 socket.socket(fileno=args.source_fd): (args.source_ip, 8080)}
    running = True

    def stop(*_):
        nonlocal running
        running = False

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    # Bound accepted work as well as worker threads (Executor's queue is unbounded).
    futures = set()
    with concurrent.futures.ThreadPoolExecutor(max_workers=32) as pool:
        while running:
            readable, _, _ = select.select(list(listeners), [], [], .2)
            futures = {f for f in futures if not f.done()}
            for listener in readable:
                client, _ = listener.accept()
                if len(futures) >= 32:
                    client.close()
                else:
                    futures.add(pool.submit(forward, client, listeners[listener]))
        for listener in listeners:
            listener.close()
        # Controller bounds shutdown and can kill only this recorded process if
        # an existing long response is still draining. No detached child workers.


if __name__ == "__main__":
    main()
