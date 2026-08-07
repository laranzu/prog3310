
""" Link creation for routing simulator.

    This module provides a multicast group protocol for programs
    to self-configure into subnets with one or more simulated
    point to point links, and functions for the actual routing
    protocol to get the available links.
    
    Routing program should call
        links.start(the-routing-object)
        ...
        links.stop()
    where the-routing-object is a delegate that handles new links.
    See the LinkDelegate spec later in code.

    Can run stand alone for testing from parent dir
        python -m PyDV.links

"""

#   There is no configuration needed other than a multicast group address.
#   Each host exchanges messages to randomly distribute connections
#   across all participating hosts.
#
#   The protocol has two messages:
#       JOIN        Sent by newly created node, and by nodes that
#                   want more links. For an interesting simulation
#                   really want more than one link per node.
#       LINK <ip>   Response to JOIN, offer to establish link to
#                   node at <ip>, listen on TCP socket. Joiner
#                   acknowledges offer by connecting over TCP.
#
#   Nodes that receive JOIN wait a random delay before sending a LINK
#   offer, with delay increased by number of links already established.
#   This distributes links over nodes more evenly.
#
#   A LINK does not have to be acknowledged. A node that has already
#   acknowledged an offer from another node can just not connect.
#
#   In version 1 there was an extra message, Link ACK, and the link
#   layer did not open a TCP socket, instead just passing the IP
#   address up to the router. This could lead to asymmetric links
#   and other inconsistencies if a LACK got lost or the router did
#   not open a connection.
#
#   Written by Hugh Fisher, ANU, 2026
#   Released under Creative Commons CC0 Public Domain Dedication
#   This code may be freely copied and modified for any purpose

import copy, ipaddress, queue, random, socket, struct, threading, time
import logging as log

from . import mcast

# Used as a singleton, not expecting to create more than one

class Links(object):

    # Router object passed to start() should respond to these messages
    #   class _LinkDelegate(object):
    #       def newLink(self, tcpSocket):

    # The multicast group address for link formation.
    # This is NOT the address used by the routing protocol
    # NOTE: would like to use a 239. address, but those are blocked
    # in CompSci labs. 224. link local does work. Thanks Felix
    mcastGroup  = "224.0.0.70"
    # Opposite for IPv6 in CompSci: site-specific transient works, but not link-local
    # NOTE: the multicast links protocol works on IPv6, but the current lab PCS
    # do not have IPv6 addresses assigned so the router TCP sockets do not work :-(
    #mcastGroup  = "ff15::3310"
    mcastPort   = 3310
    # On non-lab PCs may need to specify interface
    mcastInterface  = None
    mcastChannel    = None

    # TCP port and passive socket for actual links
    ptpPort = 5252
    ptpSock = None

    # Minimum number links we would like to have
    preferNumLinks = 2
    # Our links, identified by IP address
    activeLinks = None
    # Multiple threads so must protect access
    activeLock = None

    # Initial time between sending JOIN requests
    joinDelay = 4.0

    # Threads and flag to shut down threads
    netThreads = []
    running = True

    # Communication between threads
    QUEUE_SIZE = 64
    messageQ = None

    ####    Setup / teardown
    @classmethod
    def start(cls, delegate=None):
        """Start the link creation protocol, notify delegate of new links"""
        log.info("Start link creation")
        # Network sockets
        cls.mcastChannel = mcast.MCastChannel(cls.mcastGroup, cls.mcastPort,
                                                cls.mcastInterface)
        cls.ptpSock = cls.createPassive()
        # Our list of links
        cls.activeLock = threading.Lock()
        cls.activeLinks = {}
        # Threads
        cls.running = True
        cls.messageQ = queue.Queue(cls.QUEUE_SIZE)
        listen = Listener(cls.mcastChannel, cls.messageQ, delegate)
        cls.netThreads.append(listen)
        output = Joiner(cls.mcastChannel, cls.messageQ, delegate)
        cls.netThreads.append(output)
        listen.start()
        output.start()

    @classmethod
    def createPassive(cls):
        """Create a TCP socket for incoming point to point"""
        if cls.ipVersion() == 6:
            anyAddr = "::"
        else:
            anyAddr = "0.0.0.0"
        sock = socket.socket(cls.ipFamily(), socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.settimeout(cls.joinDelay)
        sock.bind((anyAddr, cls.ptpPort))
        sock.listen(5)
        log.debug("Created passive PTP socket {} : {}".format(
                    sock.getsockname()[0], cls.ptpPort))
        return sock

    @classmethod
    def stop(cls):
        """Shut down"""
        cls.running = False
        for thr in cls.netThreads:
            thr.join()
        cls.netThreads = []
        try:
            cls.ptpSock.close()
        except OSError:
            // Already closed 
            pass
        for ptp in cls.activeLinks.values():
            try:
                ptp.close()
            except OSError:
                pass
        cls.activeLinks = {}
        cls.mcastChannel.close()
        log.info("Link creation shutdown")

    ####    Utility

    @classmethod
    def clock(cls):
        """Whatever the system relative clock is"""
        return time.monotonic()

    @classmethod
    def linkAddr(cls, nodeAddress):
        """Just use IP address, not port"""
        return nodeAddress[0]

    @classmethod
    def ipVersion(cls):
        """Are we IPv4 or 6?"""
        return ipaddress.ip_address(cls.mcastGroup).version

    @classmethod
    def ipFamily(cls):
        """AF_INET_WHATEVER for socket creation"""
        if cls.ipVersion() == 6:
            return socket.AF_INET6
        else:
            return socket.AF_INET

    ####        Thread safe access to links

    @classmethod
    def addLink(cls, linkSocket):
        ipAddress = cls.linkAddr(linkSocket.getpeername())
        with cls.activeLock:
            if ipAddress not in cls.activeLinks:
                cls.activeLinks[ipAddress] = linkSocket
                log.info("PTP link #{} to {}".format(len(cls.activeLinks), ipAddress))

    @classmethod
    def removeLink(cls, linkSocket):
        with cls.activeLock:
            # Socket might have been closed, so cannot lookup remote address
            keys = list(cls.activeLinks.keys())
            for k in keys:
                if cls.activeLinks[k] is linkSocket:
                    del cls.activeLinks[k]
            # No problem if already deleted

    @classmethod
    def active(cls):
        """Return list of established point to point links"""
        with cls.activeLock:
            result = copy.copy(cls.activeLinks)
        return result

##  Handle incoming messages

class Listener(threading.Thread):
    """Accept incoming messages, decide how to respond"""
    def __init__(self, group, messageQueue, linkDelegate=None):
        super().__init__()
        self.group = group
        self.messages = messageQueue
        self.delegate = linkDelegate

    def run(self):
        log.debug("Start link listener {}".format(self.group.srcAddr))
        while Links.running:
            # New messages?
            try:
                msg, sender = self.group.recv()
                if msg is None:
                    continue # Timeout
                # Multicast loopback is (probably) on so we get copies of everything we send
                if sender == self.group.srcAddr:
                    continue
                log.debug("Received {} from {}".format(msg, sender))
                # OK, what do we do?
                if msg.startswith("JOIN"):
                    self.doJoin(msg, sender)
                elif msg.startswith("LINK"):
                    self.doLink(msg, sender)
                else:
                    log.warning("Link listener unknown message type: {}".format(msg))
            except OSError:
                log.error("OS Error link group")
                Links.running = False
        log.debug("End link listener")

    def doJoin(self, msg, sender):
        # Already linked?
        if Links.linkAddr(sender) in Links.activeLinks:
            return
        # Delayed response, handled by joiner thread
        try:
            self.messages.put((msg,sender), block=False)
        except queue.Full:
            log.warning("Link queue full, drop message")

    def doLink(self, msg, sender):
        # Meant for us?
        try:
            addr = msg.split()[1].strip()
            if addr != self.group.srcAddr[0]:
                return
        except (IndexError, ) as e:
            log.warning("No address in {}".format(msg))
            return
        sender = Links.linkAddr(sender)
        # May already be linked, or someone else may have already responded to our JOIN
        if len(Links.activeLinks) < Links.preferNumLinks and sender not in Links.activeLinks:
            log.debug("Try active link to {}".format(sender))
            linkSock = socket.socket(Links.ipFamily(), socket.SOCK_STREAM)
            try:
                linkSock.settimeout(Links.joinDelay)
                linkSock.connect((sender, Links.ptpPort))
                Links.addLink(linkSock)
                if self.delegate:
                    self.delegate.newLink(linkSock)
                log.debug("Active PTP link to {}".format(sender))
            except (socket.timeout, TimeoutError):
                log.warning("Could not connect to link offer from {}".format(sender))
        else:
            log.debug("Ignore link from {}".format(sender))
        

##  Request link creation

class Joiner(threading.Thread):
    """Send JOIN and LINK requests"""

    def __init__(self, group, messageQueue, linkDelegate=None):
        super().__init__()
        self.group = group
        self.messages = messageQueue
        self.delegate = linkDelegate

    def run(self):
        log.debug("Start link joiner {}".format(self.group.srcAddr))
        # Initial request
        self.group.send("JOIN")
        log.debug("Send JOIN")
        nextJoin = Links.clock() + Links.joinDelay
        try:
            while Links.running:
                # JOIN to process?
                try:
                    request = self.messages.get(block=True, timeout=1.0)
                    self.respondJoin(request)
                except queue.Empty:
                    pass
                # Want more links?
                if len(Links.activeLinks) < Links.preferNumLinks:
                    now = Links.clock()
                    if now > nextJoin:
                        self.group.send("JOIN")
                        log.debug("Send JOIN")
                        nextJoin = now + Links.joinDelay
        except OSError as e:
            log.error("OS Error send link group: {}".format(e))
            Links.running = False
        log.debug("End link joiner")

    def respondJoin(self, request):
        """Delayed response to JOIN"""
        msg = request[0]
        source = request[1]
        # Random delay, plus extra for each existing link. This sleep
        # also means we only respond to one JOIN at a time
        time.sleep(random.uniform(0, Links.joinDelay) + len(Links.activeLinks) * Links.joinDelay)
        log.debug("Offer link to {}".format(source))
        self.group.send("LINK {}".format(Links.linkAddr(source)))
        try:
            log.debug("Wait for PTP connect")
            linkSock, peer = Links.ptpSock.accept()
            log.debug("Accepted PTP")
            Links.addLink(linkSock)
            if self.delegate:
                self.delegate.newLink(linkSock)
        except (socket.timeout, TimeoutError):
            log.warning("No response to LINK offer")


####

if __name__ == "__main__":
    # Testing link creation
    log.basicConfig(format="%(levelname)s %(message)s", datefmt="%H:%M:%S", level=log.DEBUG)
    Links.start()
    time.sleep(60)
    Links.stop()
