# Sandbox for compiling and running LLM-generated C/C++ tests.
# Runs with --network none, so every tool must be baked in here.
FROM gcc:13

RUN apt-get update \
    && apt-get install -y --no-install-recommends gcovr \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /work
