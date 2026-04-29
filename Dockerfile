# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

RUN mkdir -p /workspace/extracted \
 && cp build/libs/*.jar /workspace/extracted/app.jar \
 && cd /workspace/extracted \
 && java -Djarmode=tools -jar app.jar extract --layers --destination layered \
 && rm app.jar

FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S app && adduser -S app -G app -h /app

WORKDIR /app

COPY --from=builder --chown=app:app /workspace/extracted/layered/dependencies/ ./
COPY --from=builder --chown=app:app /workspace/extracted/layered/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /workspace/extracted/layered/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /workspace/extracted/layered/application/ ./

USER app

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Duser.timezone=UTC"

EXPOSE 8080


HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/health" >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
