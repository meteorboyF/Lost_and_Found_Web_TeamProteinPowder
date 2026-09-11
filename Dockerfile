# =============================================================================
# Lost & Found — container build
#
# Two stages: a JDK image compiles the jar, a JRE image runs it. The compiled
# stylesheet is committed to the repo, so no Node stage is needed — the build
# inputs are exactly what a teammate clones.
# =============================================================================

# ---- build ------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Dependency layer first, so source edits don't re-download the world.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B -DskipTests package

# ---- run --------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user; pre-create the writable dirs so mounted volumes
# inherit sane ownership.
RUN useradd --system --home /app appuser \
    && mkdir -p /app/db /app/uploads \
    && chown -R appuser:appuser /app

COPY --from=build /build/target/*.jar app.jar

# H2 database file and uploaded photographs live here — mount volumes over
# both or the data dies with the container.
VOLUME ["/app/db", "/app/uploads"]

ENV JAVA_OPTS=""
EXPOSE 8080
USER appuser

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
