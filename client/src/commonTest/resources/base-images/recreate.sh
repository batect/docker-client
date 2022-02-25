#! /usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

function main() {
    for path in "$ROOT_DIR"/*; do
        if [[ -d "$path" ]]; then
            build "$path"
        fi
    done

    echo "All images rebuilt."
}

function build() {
    local directory=$1

    echo "Building $directory..."
    local name
    name=$(basename "$directory")

    local tag="ghcr.io/batect/docker-client:$name"
    docker build --platform linux/amd64 --tag "$tag" "$directory"
    docker push "$tag"

    echo
}

main
