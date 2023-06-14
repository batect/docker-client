# syntax=docker/dockerfile:1.4.0
FROM alpine:3.18.2

RUN apk add --no-cache openssh-client-common

RUN --mount=type=ssh,required=true echo "SSH agent is available!" && ssh-add -l
