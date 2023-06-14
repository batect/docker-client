# syntax=docker/dockerfile:1.4.0
FROM alpine:3.18.2

RUN --mount=type=secret,id=the-secret,target=/the-secret.txt echo "The secret is:" && cat /the-secret.txt && echo
