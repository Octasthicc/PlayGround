#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"
: "${RUNTIME_DIR:?RUNTIME_DIR must point to the OMGithub runtime}"
: "${CAPTURE_URL:?Set CAPTURE_URL to the exact preview URL to capture}"
: "${CAPTURE_DIR:?Set CAPTURE_DIR to the evidence output directory}"

/usr/bin/time -p mkdir -p "$CAPTURE_DIR"
export CAPTURE_STRICT=1
export CAPTURE_READY_SELECTOR='main#preview-ready'
echo "Capturing $CAPTURE_URL at desktop and mobile viewports into $CAPTURE_DIR"
/usr/bin/time -p node "$RUNTIME_DIR/scripts/default-capture.mjs"
