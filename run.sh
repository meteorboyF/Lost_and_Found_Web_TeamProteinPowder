#!/usr/bin/env bash
# Start the application for local development.
#
# Pins JAVA_HOME to a real JDK: some machines have a JRE-only java on PATH,
# which builds fail against with "release version 21 not supported".
set -euo pipefail

cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/javac" ]; then
  for candidate in \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-25-openjdk-amd64 \
    /usr/lib/jvm/default-java; do
    if [ -x "$candidate/bin/javac" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

echo "Using JAVA_HOME=${JAVA_HOME:-<system default>}"

# Compile the stylesheet first so a fresh clone serves real CSS.
if [ -d frontend/node_modules ]; then
  (cd frontend && npm run --silent build)
else
  echo "frontend/node_modules missing — run 'npm install' in ./frontend to rebuild CSS"
fi

exec ./mvnw -q -B spring-boot:run
