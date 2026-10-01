# Sandbox for running LLM-generated pytest suites.
# Runs with --network none, so every dependency must be baked in here.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN pip install pytest pytest-cov

WORKDIR /work
