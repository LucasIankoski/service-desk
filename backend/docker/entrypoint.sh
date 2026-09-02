#!/usr/bin/env sh
set -eu

if [ -n "${APP_ENCRYPTION_KEY_FILE:-}" ] && [ -f "$APP_ENCRYPTION_KEY_FILE" ]; then
  APP_ENCRYPTION_KEY="$(tr -d '\r\n' < "$APP_ENCRYPTION_KEY_FILE")"
  export APP_ENCRYPTION_KEY
fi

exec java ${JAVA_OPTS:-} -jar /app/app.jar
