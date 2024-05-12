#!/usr/bin/env python

"""
    Very very very simple SSL web server program for ANU COMP3310.

    Run with
        python sslServer.py [ port ]

    Written by Hugh Fisher u9011925, ANU, 2024
    Released under Creative Commons CC0 Public Domain Dedication
    This code may be freely copied and modified for any purpose
"""

import sys
import pprint, socket, ssl
# Keep the code short for this tiny program
from socket import *

# Shared by client and server
import sockLine
from sockLine import readLine, writeLine


# IP address and port. Assumes running unprivileged
serviceHost = "localhost"
servicePort = 3310


def serverSSL(host, port):
    """Return new server socket and context"""
    # Underlying socket
    sock = socket(AF_INET, SOCK_STREAM)
    sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(5)
    # SSL/TLS
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.check_hostname = False
    context.load_default_certs(purpose=ssl.Purpose.CLIENT_AUTH)
    #   OR
    #   context.load_cert_chain(certificate file, key file, password)
    # Unlike client side, servers use base socket and context
    return sock, context

def clientLoop(host, port):
    """Accept client connections on given host and port"""
    # Set up passive socket and SSL/TLS
    serverSock, context = serverSSL(host, port)
    while True:
        try:
            client, clientAddr = serverSock.accept()
        # If something goes wrong with the network, we will stop
        except OSError as e:
            print(type(e).__name__, "in clientLoop", e.args)
            break
        # Now create encrypted connection to client
        #sslSock = context.wrap_socket(client, server_side=True)
        sslSock = client
        print("Created server socket for", sslSock.getsockname()[0],
                                        sslSock.getsockname()[1])
        print("Accepted client connection from", clientAddr)
        # particular client. Use for requests and replies.
        singleRequest(sslSock)
        #
    print("Close server socket")
    serverSock.close()

def singleRequest(sock):
    """Single HTTP request for a single client"""
    try:
        # We only handle GET / and don't care about request headers
        request = readLine(sock)
        while True:
            header = readLine(sock)
            if header is None or len(header.rstrip()) == 0:
                break
        print("Server received", request)
        writeLine(sock, "HTTP/1.0 404 Server has no resources")
        writeLine(sock, "")
    # Try not to crash if the client does something wrong
    except OSError as e:
        print(type(e).__name__, "in serverLoop", e.args)
    print("Close client socket")
    sock.close()


def processArgs(argv):
    """Handle command line arguments"""
    global servicePort
    #
    if len(argv) > 1:
        servicePort = int(argv[1])

##

if __name__ == "__main__":
    processArgs(sys.argv)
    clientLoop(serviceHost, servicePort)
    print("Done.")
