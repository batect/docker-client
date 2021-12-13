FROM alpine:3.14.2 AS other

RUN touch /file-from-other

FROM alpine:3.14.2

RUN echo "This stage should never be used!">/dev/stderr && exit 1
