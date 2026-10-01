#!/usr/bin/env sh
# Builds the Docker images used by the AI test generation sandbox.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

docker build -t grader-sandbox-cpp:1 "$DIR/cpp"
docker build -t grader-sandbox-py:1 "$DIR/python"
