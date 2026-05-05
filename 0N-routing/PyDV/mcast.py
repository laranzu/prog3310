
""" Multicast IP group channel.

    Multicast is conceptually the same as UDP on a socket,
    but in practice it is much more complicated :-(
    Two sockets, one for sending and one for receiving,
    work best across multiple platforms; and there are
    differences between IPv4 and IPv6 group membership.

    Written by Hugh Fisher, ANU, 2026
    Released under Creative Commons CC0 Public Domain Dedication
    This code may be freely copied and modified for any purpose
"""

import ipaddress, socket, struct
import logging as log

PKT_SIZE = 1024

class MCastChannel(object):
    """Multicast group communication channel"""

    def __init__(self, IPaddress, portNumber):
        """Create and connect new socket for group address"""
        self.address  = ipaddress.ip_address(IPaddress)
        self.destPort = portNumber
        self.srcAddr  = ("", 0)
        # Not used at the moment
        self.seqNo    = 1
        self.createSockets()
        log.info("Connected to group channel {} : {}".format(self.address, self.destPort))

    def createSockets(self):
        """Input and output sockets for group channel"""
        # There are annoying differences between IPv4 and IPv6
        ipv6 = self.address.version == 6
        # For listening
        log.debug("Create input socket for {} : {}".format(self.address, self.destPort))
        if ipv6:
            family = socket.AF_INET6
            anyAddr = "::"
        else:
            family = socket.AF_INET
            anyAddr = "0.0.0.0"
        self.input = socket.socket(family, socket.SOCK_DGRAM)
        self.input.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        # Should really bind to interface for multicast address, but hard in Python.
        # Binding to the multicast address does work on Linux/MacOS, but not MSWin :-(
        self.input.bind((anyAddr, self.destPort))
        self.input.settimeout(1.0)
        log.debug("Add membership for {}".format(self.address))
        if ipv6:
            ipv6_mreq = struct.pack('!16sI', self.address.packed, 0)
            self.input.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_JOIN_GROUP, ipv6_mreq)
        else:
            ip_mreqn = struct.pack('!4sIH', self.address.packed, socket.INADDR_ANY, 0)
            self.input.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, ip_mreqn)
        # For sending
        log.debug("Create output socket for {} : {}".format(self.address, self.destPort))
        self.output = socket.socket(family, socket.SOCK_DGRAM)
        self.output.connect((self.address.compressed, self.destPort))
        # Want own source address for detecting loopbacks and name collisions
        # but just address and port, no IPv6 flow and scope
        self.srcAddr = self.output.getsockname()[0:2]
        log.debug("Source address on send {}".format(self.srcAddr))
        #
        log.debug("MCastChannel sockets created")

    def close(self):
        """Close channel"""
        if self.address.version == 6:
            ipv6_mreq = struct.pack('!16sI', self.address.packed, 0)
            self.input.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_LEAVE_GROUP, ipv6_mreq)
        else:
            ip_mreqn = struct.pack('!4sIH', self.address.packed, socket.INADDR_ANY, 0)
            self.input.setsockopt(socket.IPPROTO_IP, socket.IP_DROP_MEMBERSHIP, ip_mreqn)
        self.input.close()
        self.output.close()
        log.debug("Closed channel")

    def send(self, message):
        """Send text message, bump sequence number"""
        self.output.send(message.encode('UTF-8'))
        self.seqNo += 1

    def recv(self):
        """Return next message including header, sender IP"""
        try:
            msg, src = self.input.recvfrom(PKT_SIZE)
            if msg is not None:
                msg = msg.decode('utf-8', 'backslashreplace')
            # Strip flow and scope from IPv6 address so just addr, port like IPv4
            if self.address.version == 6:
                src = src[0:2]
        except (socket.timeout, TimeoutError):
            msg = None
            src = None
        return msg, src


