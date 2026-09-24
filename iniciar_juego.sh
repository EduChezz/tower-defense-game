#!/usr/bin/env bash
# Compila el backend (Java) y arranca el servidor del juego. Abre http://127.0.0.1:8080/ en el navegador.
set -e
cd "$(dirname "$0")/backend"
mkdir -p out
javac -encoding UTF-8 -d out src/*.java
echo "Juego en http://127.0.0.1:8080/  (Ctrl+C para detenerlo)"
( sleep 2; (xdg-open http://127.0.0.1:8080/ || open http://127.0.0.1:8080/) >/dev/null 2>&1 ) &
exec java -cp out ServidorApi ../frontend 8080
