# Multi-stage build: compile with a full JDK+Maven image, run on a slim JRE.
# Keeps the final image small (no build tools, no source, no Maven cache)
# and works the same locally (`docker build .`) as on Render, which builds
# this Dockerfile directly rather than needing a separate buildpack.

FROM eclipse-temurin:26-jdk AS build
WORKDIR /app

# Copy the Maven wrapper and pom first so dependency resolution is cached
# in its own Docker layer - only re-runs when pom.xml actually changes,
# not on every source edit.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:26-jre AS run
WORKDIR /app

# Runs as a non-root user - a container running as root is an unnecessary
# privilege-escalation risk if the JVM or a dependency is ever compromised.
RUN useradd --system --create-home --shell /usr/sbin/nologin raillens
USER raillens

COPY --from=build /app/target/*.jar app.jar

# Render (and most PaaS hosts) inject PORT at runtime and route external
# traffic to it - server.port must follow that, not a hardcoded value, or
# the platform's health checks and routing never reach the app.
ENV PORT=8080
EXPOSE 8080

# SPRING_DATASOURCE_URL is assembled here rather than set directly by Render,
# because Render's Postgres "connectionString" is a plain postgres:// URI,
# not the jdbc:postgresql:// form Spring's DataSourceProperties requires.
# DB_HOST/DB_PORT/DB_NAME come from render.yaml's fromDatabase env vars.
#
# -XX:MaxRAMPercentage=75 - without an explicit heap flag, the JVM's default
# ergonomics cap the heap at ~25% of container memory (~128MB on Render's
# free 512MB plan). That's too little headroom once Tomcat, HikariCP, and
# the Caffeine caches (see CacheConfig) are also holding data, and is what
# caused the 2026-08-05 production OutOfMemoryError under a concurrent
# request burst. 75% leaves ~25% (~128MB) for thread stacks, metaspace, and
# JIT/GC overhead outside the heap - Temurin detects the container's cgroup
# memory limit correctly, so this scales automatically if the Render plan
# is ever upgraded to more RAM.
# -XX:+ExitOnOutOfMemoryError - if the heap is ever exhausted again, exit
# the process immediately so Render's health check fails fast and restarts
# a clean instance, instead of limping along in a half-broken state serving
# intermittent 500s.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -jar app.jar --server.port=${PORT} --spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"]
