# syntax=docker/dockerfile:1.4.0
FROM alpine:3.16.2

RUN --mount=type=ssh,required=true echo "SSH agent is available!" && ls -la "$SSH_AUTH_SOCK"
