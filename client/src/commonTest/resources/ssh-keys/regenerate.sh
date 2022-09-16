#! /usr/bin/env bash

set -euo pipefail

KEY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

rm -rf "$KEY_DIR/id_rsa"
rm -rf "$KEY_DIR/id_rsa.pub"

ssh-keygen -t rsa -b 4096 -C "docker-client-test-ssh-key@batect.dev" -f "$KEY_DIR/id_rsa" -N ""

echo "Done."
