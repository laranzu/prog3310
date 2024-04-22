#!/usr/bin/env python

"""
    SSL web connection program for ANU COMP3310.

    Run with
        python sslClient.py [ IP addr ] [ port ]

    Reads and sends input lines from terminal until blank line.
    (In other words, a HTTP request.) Half closes socket.
    Then reads and prints responses from server until closed.

    Written by Hugh Fisher u9011925, ANU, 2024
    Released under Creative Commons CC0 Public Domain Dedication
    This code may be freely copied and modified for any purpose
"""

import sys
import socket, ssl
from socket import *
from pprint import pprint


# Default IP address and port that client will contact
serviceHost = "www.anu.edu.au"
servicePort = 443


def openSSL(hostName, port):
    """Return new SSL socket and context to hostName:port"""
    # Start with TCP socket
    sock = socket(AF_INET, SOCK_STREAM)
    sock.connect((hostName, port))
    print("Client connected to", sock.getpeername()[0], sock.getpeername()[1])
    # Convert to SSL
    context = ssl.create_default_context()
    sslSock = context.wrap_socket(sock, server_hostname=hostName)
    # Initial secure handshake
    sslSock.do_handshake()
    print("Version:", sslSock.version())
    print("Cipher: ", sslSock.cipher())
    print("Certificate:")
    pprint(sslSock.getpeercert(False))
    # Return both, although we mostly just use the socket
    return sslSock, context



def inputLoop(host, port):
    """Read input until blank line. Send as request to host, print response"""
    # Set up SSL
    sock, ctx = openSSL(host, port)
    sock.close()

def sendRequest(sock, request):
    """Send our request to server"""
    # No try: if anything goes wrong, higher level will handle
    writeLine(sock, request)
    print("Sent request to server")


def readReply(sock):
    """Read and print server response"""
    reply = readLine(sock)
    print(reply)


def processArgs(argv):
    """Handle command line arguments"""
    global serviceHost, servicePort
    #
    # This program has only two CLI arguments, and we know the order.
    # For any program with more than two args, use a loop or look up
    # the standard Python argparse library.
    if len(argv) > 1:
        serviceHost = argv[1]
        if len(argv) > 2:
            servicePort = int(argv[2])

##

if __name__ == "__main__":
    processArgs(sys.argv)
    inputLoop(serviceHost, servicePort)
    print("Done.")
