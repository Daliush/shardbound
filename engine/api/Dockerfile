# The game server. Build context: the repository root, since the server loads content/ at startup.
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
ENV SHARDBOUND_CONTENT_DIR=/app/content
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
