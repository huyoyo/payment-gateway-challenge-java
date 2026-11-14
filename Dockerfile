# ---------- build ----------
FROM docker.xuanyuan.run/library/maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY . .
RUN mvn -B -DskipTests package

# ---------- runtime ----------
FROM docker.xuanyuan.run/library/eclipse-temurin:17-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 appuser

WORKDIR /app
COPY --from=build /workspace/gateway-web/target/gateway-web-*.jar /app.jar
RUN chown appuser:appuser /app.jar
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=12 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app.jar"]
