# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline -DskipTests
COPY src src
RUN ./mvnw -q -B clean package -DskipTests

# Debian-based on purpose: the ONNX runtime used for local embeddings needs glibc.
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tesseract-ocr tesseract-ocr-eng tesseract-ocr-ben \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system notemind && useradd --system --gid notemind --create-home notemind \
    && mkdir -p /app/data/uploads /app/data/model-cache && chown -R notemind:notemind /app
WORKDIR /app
COPY --from=build --chown=notemind:notemind /workspace/target/notemind.jar app.jar
USER notemind
ENV NOTEMIND_STORAGE_DIR=/app/data/uploads \
    NOTEMIND_MODEL_CACHE=/app/data/model-cache \
    DJL_CACHE_DIR=/app/data/model-cache/djl \
    ENGINE_CACHE_DIR=/app/data/model-cache/djl \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xmx1500m"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=30 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
