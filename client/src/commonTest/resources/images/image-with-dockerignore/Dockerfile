FROM alpine:3.17.2

RUN apk add --no-cache tree

RUN mkdir -p /files
COPY . /files
RUN rm -f /files/.DS_Store

ARG CACHE_BUSTER
RUN tree -J --noreport -u -g -s --sort=name /files
