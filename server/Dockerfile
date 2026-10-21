# Eclipse Temurin 21.0.12.1+1 JRE / Ubuntu 24.04, immutable multi-platform index.
# The local build script supplies only locked runtime JARs, never the source tree.
FROM eclipse-temurin:21-jre-noble@sha256:35501d3f93173b420ac9001658eb68157e813316b865087e345c187fca422c4c
LABEL org.opencontainers.image.title="Async Tabletop Wizards server" \
      org.opencontainers.image.base.name="eclipse-temurin:21-jre-noble" \
      org.opencontainers.image.base.digest="sha256:35501d3f93173b420ac9001658eb68157e813316b865087e345c187fca422c4c"
WORKDIR /opt/async-tt-wiz
COPY --chown=0:0 lib/ ./lib/
# Numeric identity needs neither root nor a mutable home; mounted secrets must be
# readable by this UID. The image contains no database credentials or defaults.
USER 10001:10001
STOPSIGNAL SIGTERM
EXPOSE 8080 8081
# Java is PID 1; do not interpose a shell or the base's certificate entrypoint.
# An explicit serve or migrate argument is required, just as in the distribution.
ENTRYPOINT ["/opt/java/openjdk/bin/java", "-cp", "/opt/async-tt-wiz/lib/*", "org.asyncttwiz.server.ServiceCommand"]
