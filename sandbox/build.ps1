# Builds the Docker images used by the AI test generation sandbox.
# Failure is detected via $LASTEXITCODE: docker writes build progress to stderr,
# which Windows PowerShell 5.1 would treat as an error under ErrorActionPreference=Stop.
$dir = $PSScriptRoot

docker build -t grader-sandbox-cpp:1 (Join-Path $dir 'cpp')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
docker build -t grader-sandbox-py:1 (Join-Path $dir 'python')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
