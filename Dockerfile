
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
# Package, then unpack into app.jar + lib/: the AOT cache below needs a plain classpath, not a nested fat jar.
RUN ./mvnw -B -DskipTests package \
 && java -Djarmode=tools -jar target/train-db-*.jar extract --application-filename app.jar --destination /app/extracted

FROM eclipse-temurin:25-jre AS run
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin raillens

COPY --from=build /app/extracted/ ./

ENV PORT=8080
EXPOSE 8080

# Sized for Render's free plan (512 MB, a fraction of one CPU), measured with the full timetable loaded and warmed:
# the heap peaks under 144 MB while the timetable snapshot streams in and holds about 70 MB after; total memory
# use settles around 435 MB. -Xmx is explicit because MaxRAMPercentage alone left too little room for the
# ~110 MB of class metadata and JIT code outside the heap. Serial GC and fewer malloc arenas suit one small CPU.
# Override with JAVA_OPTS. (A JAVA_TOOL_OPTIONS env var on Render, if still set, is applied first and overridden here.)
ENV MALLOC_ARENA_MAX=2
ENV JAVA_OPTS="-Xmx192m -Xss512k -XX:+UseSerialGC -XX:ReservedCodeCacheSize=48m -XX:+ExitOnOutOfMemoryError"

# AOT cache (JDK 25): classes already loaded and linked, plus method profiles, from one training start. Measured
# on one CPU core: about 18 s -> 8 s to a ready context; the full app with the real timetable 5.6 s instead of
# 11-12 s. The training run exits right after the context refreshes (no web server, no warm-up) and needs no
# database: the settings below skip every connection, and the JWT secret is a throwaway used only for this run.
# It uses the same JAVA_OPTS as production (the cache only works with matching JVM flags). If training fails, the
# image still builds and the app starts without the cache: a missing cache only logs an error.
RUN java $JAVA_OPTS -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app.jar \
      --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none \
      --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      --spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      --spring.datasource.url=jdbc:postgresql://aot-training.invalid:5432/x \
      --raillens.cache.warmup.enabled=false --raillens.jwt.secret=aot-training-only-never-used-at-runtime-0123456789 \
      > /tmp/aot-training.log 2>&1 \
 || { echo "AOT training failed; the image will start without the cache"; tail -n 20 /tmp/aot-training.log; }; \
    rm -f /tmp/aot-training.log

USER raillens

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -XX:AOTCache=app.aot -jar app.jar --server.port=${PORT} \"--spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require&currentSchema=public\""]
