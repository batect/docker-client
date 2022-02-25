# Base image

This directory contains dummy base images used to test image pull progress reporting while building and pulling images.

We use our own base images here to avoid the pain associated with making sure the images we use for testing are not present on the
machine, including any references as base images for other images.

If you need to recreate them, run [`./recreate.sh`](recreate.sh).
