#! /usr/bin/env sh

set -euo pipefail

mkdir -p /healthchecks
touch "/healthchecks/$RANDOM"
echo "Also healthy!"
