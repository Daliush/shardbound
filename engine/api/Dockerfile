# The game server, for docker compose and Google Cloud Run (engine/README.md). Build context: the repository root,
# since the server loads content/ at startup and reads the Arbiter's evaluations (evals/reports/), their datasets
# (evals/datasets/) and the cards' art (assets/art/web/) at each request.
# Tests are not run here: CI runs them.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY engine/ engine/
COPY content/ content/
RUN --mount=type=cache,target=/root/.m2 cd engine && sh ./mvnw -B -q -DskipTests -pl api -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/engine/api/target/shardbound-api-*.jar app.jar
COPY content/ content/
COPY evals/reports/ reports/
COPY evals/datasets/ datasets/
COPY assets/art/web/ art/
ENV SHARDBOUND_CONTENT_DIR=/app/content
ENV SHARDBOUND_REPORTS_DIR=/app/reports
ENV SHARDBOUND_DATASETS_DIR=/app/datasets
ENV SHARDBOUND_ART_DIR=/app/art
# The JVM takes a quarter of the container's memory by default; the server is the only process here.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
