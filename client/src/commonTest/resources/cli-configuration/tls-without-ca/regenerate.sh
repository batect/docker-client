#! /usr/bin/env bash

# Based on https://docs.docker.com/engine/security/https/ and https://gist.github.com/ivan-pinatti/6ad05557e526f1f32ca357d15139df83

set -euo pipefail

CERTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PASSPHRASE=abcd1234

rm -rf "$CERTS_DIR"/*.pem
rm -rf "$CERTS_DIR"/*.cnf
rm -rf "$CERTS_DIR"/*.csr

echo "Generating CA key and cert..."
openssl genrsa -aes256 -out "$CERTS_DIR/ca-key.pem" -passout pass:"$PASSPHRASE" 4096
openssl req -new -x509 -days 3650 -key "$CERTS_DIR/ca-key.pem" -sha256 -passin pass:"$PASSPHRASE" -subj "/CN=localhost/O=Internet/C=AU" -out "$CERTS_DIR/ca.pem"

echo "Generating client key and signing request..."
openssl genrsa -out "$CERTS_DIR/key.pem" 4096
openssl req -subj '/CN=client' -new -key "$CERTS_DIR/key.pem" -out "$CERTS_DIR/client.csr"
echo extendedKeyUsage = clientAuth > "$CERTS_DIR/extfile-client.cnf"
openssl x509 -req -days 3650 -sha256 -in "$CERTS_DIR/client.csr" -CA "$CERTS_DIR/ca.pem" -CAkey "$CERTS_DIR/ca-key.pem" -CAcreateserial -out "$CERTS_DIR/cert.pem" -extfile "$CERTS_DIR/extfile-client.cnf" -passin pass:"$PASSPHRASE"

echo "Cleaning up..."
rm -rf "$CERTS_DIR"/*.csr
rm -rf "$CERTS_DIR"/*.cnf
rm -rf "$CERTS_DIR"/*.srl
rm -rf "$CERTS_DIR"/ca.pem
rm -rf "$CERTS_DIR"/ca-key.pem

echo "Done."
