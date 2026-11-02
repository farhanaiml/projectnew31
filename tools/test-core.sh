#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .local-test
kotlinc app/src/main/java/com/deepscan/app/DiskScanEngine.kt tools/LocalJvmSmoke.kt -include-runtime -d .local-test/smoke.jar
java -jar .local-test/smoke.jar
