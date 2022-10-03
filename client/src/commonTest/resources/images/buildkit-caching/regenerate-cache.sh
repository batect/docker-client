#! /usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

rm -rf "$SCRIPT_DIR/cache"
mkdir -p "$SCRIPT_DIR/cache"

{
    cd "$SCRIPT_DIR/image"
    docker buildx build --cache-to "type=local,dest=$SCRIPT_DIR/cache,mode=max" .
}

echo "Done."
