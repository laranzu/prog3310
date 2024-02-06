#!/usr/bin/env python

"""
    Python TCP echo server program for ANU COMP3310.

    Run with
        python tcpServer.py [ IP addr ] [ port ]

    Written by H Fisher u9011925, ANU, 2024
    This code may be freely copied and modified
"""

import sys
import socket
# Keep the code short for this tiny program
from socket import *

# Shared by client and server
from sockLine import readLine, writeLine


# IP address and port
serviceHost = "0.0.0.0"
servicePort = 3310


def clientLoop(host, port):
    """Accept client connections on given host and port"""
    # Create a TCP socket
    serverSock = socket(AF_INET, SOCK_STREAM)
    # Address and port clients will connect to
    serverSock.bind((host, port))
    # This is the limit on established TCP connections that have not yet
    # been accepted. (Not the number of connections.) For now, it is magic.
    serverSock.listen(5)
    print("Created server socket for", serverSock.getsockname()[0],
                                        serverSock.getsockname()[1])
    while True:
        # A TCP server is different to UDP. Instead of packets,
        # we get connection requests from clients.
        try:
            client, clientAddr = serverSock.accept()
        except OSError:
            break
        print("Accepted client connection from", clientAddr)
        # Each connection accepted creates a new socket for that
        # particular client. Use for requests and replies.
        serverLoop(client)
        #
    print("Close server socket")
    serverSock.close()

def serverLoop(sock):
    """Echo service for a single client"""
    # Read and respond until client shuts down the socket,
    # using shared line read/write code
    while True:
        request = readLine(sock)
        if request is None:
            break
        writeLine(sock, "ACK: " + request)
    print("Close client socket")
    sock.close()


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
    clientLoop(serviceHost, servicePort)
    print("Done.")
