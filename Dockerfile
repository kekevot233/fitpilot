# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS web-build

WORKDIR /workspace/web
COPY web/package.json web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY web/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build

WORKDIR /workspace/backend
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=web-build /workspace/web/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S fitpilot && adduser -S fitpilot -G fitpilot

COPY --from=backend-build /workspace/backend/target/fitpilot-backend-*.jar /app/app.jar
COPY deploy/entrypoint.sh /app/entrypoint.sh
RUN chmod 755 /app/entrypoint.sh

USER fitpilot
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]