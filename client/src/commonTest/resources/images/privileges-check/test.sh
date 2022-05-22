#!/usr/bin/env sh

set -euo pipefail

# Adapted from https://stackoverflow.com/a/32144661/1668119

# NET_ADMIN is not part of the default capability set (https://github.com/moby/moby/blob/master/oci/caps/defaults.go#L6-L19)
if ip link add dummy0 type dummy 2>/dev/null ; then
    ip link delete dummy0

    echo "Container has NET_ADMIN capability"
else
    echo "Container does not have NET_ADMIN capability"
fi

# CHOWN is part of the default capability set (https://github.com/moby/moby/blob/master/oci/caps/defaults.go#L6-L19)
if chown guest /bin 2>/dev/null ; then
    echo "Container has CHOWN capability"
else
    echo "Container does not have CHOWN capability"
fi
