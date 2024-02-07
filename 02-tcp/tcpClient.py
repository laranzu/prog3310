#!/usr/bin/env python

"""
    Python TCP echo client program for ANU COMP3310.

    Run with
        python tcpClient.py [ IP addr ] [ port ]

    Written by H Fisher u9011925, ANU, 2024
    This code may be freely copied and modified
"""

import sys
import socket
# Keep the code short for this tiny program
from socket import *

# Shared by client and server
from sockLine import readLine, writeLine


# IP address and port that client will contact
serviceHost = "127.0.0.1"
servicePort = 3310


def inputLoop(host, port):
    """Read input until EOF. Send as request to host, print response"""
    # Create TCP socket
    sock = socket(AF_INET, SOCK_STREAM)
    # A TCP active (client) socket must be connected to a single host
    sock.connect((host, port))
    print("Client connected to", sock.getpeername()[0], sock.getpeername()[1])
    # Keep reading lines and sending them
    while True:
        try:
            line = input()
        except EOFError:
            break
        sendRequest(sock, line)
        readReply(sock)
    print("Client close")
    writeLine(sock, "BYE")
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
