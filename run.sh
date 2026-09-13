#!/usr/bin/env bash
# Start the application for local development.
#
# Pins JAVA_HOME to a real JDK (a JRE-only java on PATH fails the build with
# "release version 21 not supported"), rebuilds the stylesheet, makes sure a
# MySQL is reachable, then boots Spring.
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

# --- Database ---------------------------------------------------------------
# If DB_URL is set, you brought your own MySQL and we trust it. Otherwise a
# dockerised MySQL 8 is started on port 3307 (not 3306, so an existing native
# server is never touched) matching the defaults in application.properties.
MYSQL_CONTAINER=lostfound-mysql

if [ -n "${DB_URL:-}" ]; then
  echo "Using externally configured database: DB_URL is set"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$MYSQL_CONTAINER"; then
  echo "Dev MySQL already running ($MYSQL_CONTAINER on :3307)"
elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  if docker ps -a --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
    echo "Starting existing dev MySQL container…"
    docker start "$MYSQL_CONTAINER" >/dev/null
  else
    echo "Creating dev MySQL 8 container on :3307 (data persists in volume lostfound-mysql-dev)…"
    docker run -d --name "$MYSQL_CONTAINER" \
      -p 3307:3306 \
      -e MYSQL_DATABASE=lostfound \
      -e MYSQL_USER=lostfound \
      -e MYSQL_PASSWORD=lostfound \
      -e MYSQL_ROOT_PASSWORD=lostfound-root \
      -v lostfound-mysql-dev:/var/lib/mysql \
      mysql:8 >/dev/null
  fi
  printf "Waiting for MySQL to accept connections"
  for _ in $(seq 1 60); do
    if docker exec "$MYSQL_CONTAINER" mysqladmin ping -h 127.0.0.1 -ulostfound -plostfound --silent >/dev/null 2>&1; then
      echo " — ready."
      break
    fi
    printf "."
    sleep 2
  done
else
  cat >&2 <<'EOF'
No database available: DB_URL is not set and Docker is not usable.
Either install/start Docker, or point the app at your own MySQL:

  sudo mysql -e "CREATE DATABASE IF NOT EXISTS lostfound CHARACTER SET utf8mb4;
                 CREATE USER IF NOT EXISTS 'lostfound'@'localhost' IDENTIFIED BY 'lostfound';
                 GRANT ALL PRIVILEGES ON lostfound.* TO 'lostfound'@'localhost';"
  export DB_URL="jdbc:mysql://127.0.0.1:3306/lostfound"

EOF
  exit 1
fi

exec ./mvnw -q -B spring-boot:run
