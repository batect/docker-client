FROM alpine:3.14.2 AS other

RUN touch /file-from-other

FROM alpine:3.14.2

COPY --from=other /file-from-other /received/file-from-other
