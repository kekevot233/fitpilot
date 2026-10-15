#!/bin/sh
set -eu

# Render exposes PostgreSQL as postgresql://user:password@host:port/database,
# while the JDBC driver requires jdbc:postgresql://host:port/database. Username
# and password stay in their dedicated environment variables, so secrets never
# need to be decoded or copied into the JDBC URL.
if [ -n "${DATABASE_URL:-}" ]; then
  database_host_port="${DATABASE_URL##*@}"
  database_host_port="${database_host_port%%/*}"
  export DB_URL="jdbc:postgresql://${database_host_port}/${DB_NAME:-fitpilot}"
fi

exec java -jar /app/app.jar "$@"