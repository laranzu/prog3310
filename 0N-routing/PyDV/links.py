
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
#   The protocol has three messages:
#       JOIN        Sent by newly created node, and by nodes that
#                   want more links. For an interesting simulation
#                   really want more than one link per node.
#       LINK <ip>   Response to JOIN, offer to establish link to node at <ip>
#       LACK <ip>   Acknowledgement of LINK offer from <ip>
#
#   Nodes that receive JOIN wait a random delay before sending a LINK
#   offer, with delay increased by number of links already established.
#   This distributes links over nodes more evenly.
#
#   A LINK does not have to be acknowledged. A node that has already
#   acknowledged an offer from another node can just not send a LACK.
#
#   TODO : if a LACK gets lost, won't have symmetric link.
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
    #       def newLink(self, senderAddress):

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

    mcastChannel   = None

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
    def start(cls, delegate):
        """Start the link creation protocol, notify delegate of new links"""
        log.info("Start link creation")
        cls.mcastChannel = mcast.MCastChannel(cls.mcastGroup, cls.mcastPort)
        # Our list of links
        cls.activeLock = threading.Lock()
        cls.activeLinks = []
        # Threads
        cls.running = True
        cls.messageQ = queue.Queue(cls.QUEUE_SIZE)
        listen = Listener(cls.mcastChannel, cls.messageQ, delegate)
        cls.netThreads.append(listen)
        output = Joiner(cls.mcastChannel, cls.messageQ)
        cls.netThreads.append(output)
        listen.start()
        output.start()

    @classmethod
    def stop(cls):
        """Shut down"""
        cls.running = False
        for thr in cls.netThreads:
            thr.join()
        cls.netThreads = []
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

    ####        Thread safe access to links

    @classmethod
    def addLink(cls, ipAddress):
        with cls.activeLock:
            if ipAddress not in cls.activeLinks:
                cls.activeLinks.append(ipAddress)
                log.info("PTP link #{} to {}".format(len(cls.activeLinks), ipAddress))

    @classmethod
    def removeLink(cls, ipAddress):
        with cls.activeLock:
            try:
                cls.activeLinks.remove(ipAddress)
                log.debug("Remove link {}".format(ipAddress))
            except (ValueError, ):
                # Harmless, already removed
                pass

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
                elif msg.startswith("LACK"):
                    self.doAck(msg, sender)
                else:
                    log.warning("Link listener unknown message type: {}".format(msg))
            except OSError:
                log.error("OS Error recv link group")
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
        # May already be linked, or someone else may have already responded to our JOIN
        if len(Links.activeLinks) < Links.preferNumLinks and addr not in Links.activeLinks:
            log.debug("Accept link from {}".format(sender))
            Links.addLink(addr)
            if self.delegate:
                self.delegate.newLink(addr)
            self.group.send("LACK {}".format(Links.linkAddr(sender)))
        else:
            log.debug("Ignore link from {}".format(sender))

    def doAck(self, msg, sender):
        #
        # Meant for us?
        try:
            addr = msg.split()[1].strip()
            if addr != self.group.srcAddr[0]:
                return
        except (IndexError, ) as e:
            log.warning("No address in {}".format(msg))
            return
        # Must be in response to our offer, so always add
        Links.addLink(addr)
        if self.delegate:
            self.delegate.newLink(addr)
        log.debug("Link ack from {}".format(sender))
        

##  Request link creation

class Joiner(threading.Thread):
    """Send JOIN and LINK requests"""

    def __init__(self, group, messageQueue):
        super().__init__()
        self.group = group
        self.messages = messageQueue

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
        except OSError:
            log.error("OS Error send link group")
            Links.running = False
        log.debug("End link joiner")

    def respondJoin(self, request):
        """Delayed response to JOIN"""
        msg = request[0]
        source = request[1]
        # Random delay, plus extra for each existing link. This sleep
        # also means we only respond to one JOIN at a time
        time.sleep(random.uniform(0, Links.joinDelay) + len(Links.activeLinks) * Links.joinDelay)
        self.group.send("LINK {}".format(Links.linkAddr(source)))
        log.debug("Offer link to {}".format(source))


##  Module level

def start(delegate=None):
    Links.start(delegate)

def stop():
    Links.stop()

def removeLink(ipAddress):
    Links.removeLink(ipAddress)

####

if __name__ == "__main__":
    # Testing link creation
    log.basicConfig(format="%(levelname)s %(message)s", datefmt="%H:%M:%S", level=log.DEBUG)
    start()
    time.sleep(60)
    stop()


