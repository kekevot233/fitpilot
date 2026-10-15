FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

# BuildKit keeps Maven's local repository outside the image layer. This reuses
# downloaded dependencies across rebuilds without dependency:go-offline, which
# also downloads many report plugins that the application never executes.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S fitpilot && adduser -S fitpilot -G fitpilot

COPY --from=build /workspace/target/fitpilot-backend-*.jar /app/app.jar

USER fitpilot
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
