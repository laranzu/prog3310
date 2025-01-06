#!/usr/bin/env python

"""
    Python UDP echo server program for ANU COMP3310.

    This is one half of a traditional Internet client-server architecture.
    As a server this program is expected to run (more or less) all the
    time, and handle requests from any client that connects.

    Run with
        python udpServer.py

    Written by Hugh Fisher u9011925, ANU, 2024
    Released under Creative Commons CC0 Public Domain Dedication
    This code may be freely copied and modified for any purpose
"""

import sys
import socket
# Keep the code short for this tiny program
from socket import *


# IP address and port that server reads requests from.
# Client needs to know these to connect.
serviceHost = "127.0.0.1"
servicePort = 3310

# Maximum client request size, in bytes
MSG_SIZE = 16       # Do not copy this


def serverLoop(host, port):
    """Run echo service on given host and port"""
    # Create an Internet UDP socket
    sock = socket(AF_INET, SOCK_DGRAM)
    # Set the address and port that we read requests from
    sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
    sock.bind((host, port))
    print("Server created socket for", sock.getsockname()[0], sock.getsockname()[1])
    # And now read and respond forever
    while True:
        try:
            message, sender = readRequest(sock)
            replyToMessage(sock, message, sender)
        except OSError:
            break
    print("Server close")
    sock.close()

def readRequest(sock):
    """Read a string up to predefined limit. Return string and sender (ie client)"""
    # In UDP we usually don't have a permanent connection to the client, so we
    # read a network packet (up to limit) and address of where it came from.
    inData, sender = sock.recvfrom(MSG_SIZE)
    # The data is just bytes. We hope it is a UTF-8 string, the Internet standard
    # for sending text, but our programming language might use another format.
    # Internet programs should decode network data as soon as received.
    # The backslashreplace is because we don't want to crash if it isn't UTF-8.
    inMessage = inData.decode('utf-8', 'backslashreplace')
    print("Server received from", sender, "request", inMessage)
    return inMessage, sender

def replyToMessage(sock, message, sender):
    """Generate reply to message"""
    # TODO: change behaviour depending on message
    reply = "ACK " + message
    print("Server sending reply", reply)
    sendReply(sock, reply, sender)

def sendReply(sock, reply, sender):
    """Send complete reply to client"""
    # UDP packets are raw bytes so text should be UTF-8, the "wire format".
    # Encoding is usually done just before sending so the rest of the program
    # doesn't need to worry about what packets look like.
    reply = reply.encode('utf-8')
    sock.sendto(reply, sender)

##

if __name__ == "__main__":
    serverLoop(serviceHost, servicePort)
    print("Done.")
