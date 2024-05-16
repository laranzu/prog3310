#!/bin/sh

# Create a sort of self signed CA for COMP3310 secure socket exercise.

echo "Authority ..."
openssl req -x509 -newkey rsa:4096 -days 3652 \
    -extensions v3_ca -addext 'keyUsage = cRLSign, keyCertSign' \
    -subj '/C=AU/ST=Australian Capital Territory/L=Canberra/O=The Australian National University/OU=COMP3310/CN=COMP3310 Demonstration Root CA' \
    -noenc -keyout ca.key -out ca.crt

echo "Host ..."
openssl req -days 3652 -extensions usr_cert \
    -addext 'subjectAltName = DNS:localhost, IP:127.0.0.1, IP:::1' \
    -subj '/C=AU/ST=Australian Capital Territory/L=Canberra/O=The Australian National University/OU=COMP3310/CN=localhost' \
    -CA ca.crt -CAkey ca.key \
    -noenc -keyout localhost.key -out localhost.crt

echo "Combine into PEM"
cat localhost.crt ca.crt > fullchain.pem
