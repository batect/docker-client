FROM alpine:3.17.3

COPY healthcheck.sh /healthcheck.sh
COPY alternative-healthcheck.sh /alternative-healthcheck.sh
HEALTHCHECK --interval=0.2s --timeout=1s --start-period=0.5s --retries=2 CMD /healthcheck.sh
