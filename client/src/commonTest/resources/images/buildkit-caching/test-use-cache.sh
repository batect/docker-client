#! /usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

{
    cd "$SCRIPT_DIR/image"
    docker buildx build --cache-from "type=local,src=$SCRIPT_DIR/cache" .
}

echo "Done."
