#!/bin/bash
set -euo pipefail

# Only run in remote environments (Claude Code on the web)
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# Download Gradle distribution, resolve dependencies, and compile source + tests.
# The container state is cached after this completes, so subsequent sessions
# will already have everything ready for fast test runs.
./gradlew :klein-lib:compileKotlinJvm :klein-lib:compileTestKotlinJvm --no-daemon
