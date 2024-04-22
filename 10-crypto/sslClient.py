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
    # Convert to SSL/TLS
    context = ssl.create_default_context()
    sslSock = context.wrap_socket(sock, server_hostname=hostName)
    # and connect
    sslSock.connect((hostName, port))
    # Initial secure handshake
    sslSock.do_handshake()
    print("Version:", sslSock.version())
    print("Cipher: ", sslSock.cipher())
    print("Certificate:")
    pprint(sslSock.getpeercert(False))
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
        sendRequest(sock, line)
        if len(line) == 0:
            break

def printResponse(sock):
    """Just print whatever the server sends us"""
    response = b""
    while True:
        line = readLine(sock)
        if line is None:
            break
        print(line, end='')

def inputLoop(host, port):
    """Connect securely to host. Send a request, print response"""
    # Set up SSL
    sock, ctx = openSSL(host, port)
    print("Client connected to", sock.getpeername()[0], sock.getpeername()[1])
    # Read and send input lines
    buildRequest(sock)
    # Print the response
    printResponse(sock)
    # 
    sock.close()
    

def sendRequest(sock, request):
    """HTTP request header"""
    request += '\r\n'
    sock.sendall(request.encode('utf-8'))

def readLine(sock):
    """HTTP reply header, or content if text"""
    inData = b''
    while True:
        ch = sock.recv(1)
        if len(ch) == 0:
            if len(inData) > 0:
                break
            else:
                return None
        inData += ch
        if ch == b'\n':
            break
    txt = inData.decode('utf-8', 'backslashreplace')
    return txt


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
