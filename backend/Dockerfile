FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY backend/src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system service-desk \
    && useradd --system --gid service-desk --home-dir /app service-desk \
    && mkdir -p /app /var/lib/service-desk/attachments \
    && chown -R service-desk:service-desk /app /var/lib/service-desk
WORKDIR /app

COPY --from=build /workspace/target/service-desk-api-*.jar /app/app.jar
COPY backend/docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh && chown service-desk:service-desk /app/entrypoint.sh /app/app.jar

USER service-desk:service-desk
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["/app/entrypoint.sh"]
