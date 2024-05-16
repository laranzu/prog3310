#!/usr/bin/env python

"""
    SSL web connection program for ANU COMP3310.

    Run with
        python sslClient.py [ IP addr ] [ port ]

    Reads and sends input lines from terminal until blank line.
    (In other words, a HTTP request.) Then reads and prints
    lines from server until closed.

    Written by Hugh Fisher u9011925, ANU, 2024
    Released under Creative Commons CC0 Public Domain Dedication
    This code may be freely copied and modified for any purpose
"""

import sys
import pprint, socket, ssl
from socket import *
from pprint import pprint

from sockLine import readLine, writeLine

# Default hostname and port that client will contact
webHost = "www.anu.edu.au"
webPort = 443


def openSSL(hostName, port):
    """Return new SSL socket and context to hostName:port"""
    # Start with TCP socket
    sock = socket(AF_INET, SOCK_STREAM)
    # Convert to SSL/TLS
    context = ssl.create_default_context()
    sslSock = context.wrap_socket(sock, server_hostname=hostName)
    # Add our own Certificate Authority to the trusted list
    context.load_verify_locations(cafile="ca.crt")
    # and connect
    sslSock.connect((hostName, port))
    # Initial secure handshake
    sslSock.do_handshake()
    print("Version:", sslSock.version())
    print("Cipher: ", sslSock.cipher())
    print("Certificate:")
    pprint(sslSock.getpeercert(False))
    print("Client connected to", sslSock.getpeername()[0], sslSock.getpeername()[1])
    # Return both, although we mostly just use the socket
    return sslSock, context

def buildRequest(sock):
    """Read input until empty line, send as request"""
    while True:
        try:
            line = input()
        except EOFError:
            break
        # Send line *then* check if it was empty
        writeLine(sock, line)
        if len(line) == 0:
            break

def printResponse(sock):
    """Just print whatever the server sends us"""
    while True:
        line = readLine(sock)
        if line is None:
            break
        print(line)

def inputLoop(host, port):
    """Connect securely to host. Send a request, print response"""
    # Set up SSL
    sock, ctx = openSSL(host, port)
    # Read and send input lines
    buildRequest(sock)
    # Print the response
    printResponse(sock)
    # 
    sock.close()


def processArgs(argv):
    """Handle command line arguments"""
    global webHost, webPort
    #
    if len(argv) > 1:
        webHost = argv[1]
        if len(argv) > 2:
            webPort = int(argv[2])

##

if __name__ == "__main__":
    processArgs(sys.argv)
    inputLoop(webHost, webPort)
    print("Done.")
