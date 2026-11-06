#!/bin/sh
# Fetches the sherpa-onnx Android library (speech recognition), which is too big to commit.
# Run once after cloning: sh scripts/fetch-sherpa-onnx.sh
set -e
VERSION=2.0.1
cd "$(dirname "$0")/../app/libs" 2>/dev/null || { mkdir -p "$(dirname "$0")/../app/libs"; cd "$(dirname "$0")/../app/libs"; }
curl -fSL -o "sherpa-onnx-$VERSION.aar" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VERSION/sherpa-onnx-$VERSION.aar"
echo "Saved app/libs/sherpa-onnx-$VERSION.aar"
