#!/usr/bin/env bash

set -euo pipefail

VERSION=$1

if [ "$VERSION" = "latest" ]; then
    PACKAGE="docker-ce"
else
    PACKAGE="docker-ce=$VERSION"
fi

# This works around 'hash sum mismatch' issues.
# See https://blog.packagecloud.io/eng/2016/03/21/apt-hash-sum-mismatch/ for more details.
echo 'Acquire::CompressionTypes::Order:: "gz";' | sudo tee /etc/apt/apt.conf.d/99compression-workaround > /dev/null

# This works around "gpg: can't connect to the agent: IPC connect call failed" errors that sometimes occur when calling "apt-key add".
pkill -9 gpg-agent || true
source <(gpg-agent --daemon)

echo "Adding Docker APT repository..."
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"

echo "Updating cached APT data..."
sudo apt-get update

echo "Installing Docker..."
sudo apt-get -y -o Dpkg::Options::="--force-confnew" --allow-downgrades install "$PACKAGE"

echo "Checking Docker version..."
docker version

echo "Done!"
