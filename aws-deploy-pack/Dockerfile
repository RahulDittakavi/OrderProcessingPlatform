FROM maven:3.9-eclipse-temurin-26 AS build

ARG SERVICE
WORKDIR /workspace

COPY ${SERVICE}/pom.xml ./pom.xml
COPY ${SERVICE}/src/ ./src/

RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --create-home app

WORKDIR /app
ARG SERVICE
COPY --from=build --chown=app:app /workspace/target/*.jar /app/app.jar

USER app
# JAVA_OPTS is empty locally (behaviour unchanged); the AWS override uses it to shrink the heap.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 $JAVA_OPTS -jar /app/app.jar"]
