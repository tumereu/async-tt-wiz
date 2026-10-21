# Python 3.13.16 / Debian bookworm; immutable official multi-platform index.
FROM python:3.13-slim-bookworm@sha256:73aa5faee130c60da030e105ec971ccf57473fbd22ce73bd6fc9ba99a40b838d
LABEL org.opencontainers.image.title="Async Tabletop Wizards synthetic test adapters" \
      org.opencontainers.image.base.digest="sha256:73aa5faee130c60da030e105ec971ccf57473fbd22ce73bd6fc9ba99a40b838d"
WORKDIR /opt/adapters
COPY e2e/adapters/adapter.py ./adapter.py
COPY tests/fixtures/catalogs/ ./catalogs/
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
USER 10001:10001
EXPOSE 8080 8081 2525
STOPSIGNAL SIGTERM
ENTRYPOINT ["python3", "/opt/adapters/adapter.py", "--fixtures", "/opt/adapters/catalogs"]
