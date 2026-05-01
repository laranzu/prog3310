
"""
    Main program for Distance Vector routing simulator.

    Run as a module from parent directory
        python -m PyDV.dvRouter [ args ]

    CLI args
        -name NAME      Router identifier, defaults hostname
        -domain NAME    Router domain, default to random from domains.txt
        -beat SECS      How often to send router messages
        -evil FILE      Advertise routes listed in FILE as well as ourself
        
        -mcast MCAST    Use different multicast address for link creation
        
        -debug          Lots and lots of internal operation detail
        -quiet          Only warnings and above
"""

#   Uses American spelling for neighbor because primary reference is
#   Interconnections: Second Edition by Radia Perlman
#
#   Written by Hugh Fisher, ANU, 2026
#   Released under Creative Commons CC0 Public Domain Dedication
#   This code may be freely copied and modified for any purpose

import argparse, copy, hashlib, ipaddress, queue, random, socket, sys, threading, time
import logging as log
from copy import deepcopy

from . import routeTable, links, sockLine
from .routeTable import CostTable, RouteEntry, RouteTable
from .sockLine import readLine, writeLine

# The port for router messages
DV_PORT = 5252

#   Design: TCP or UDP?
#   This routing simulator creates a TCP connection and control thread
#   between each pair of neighbors, which is not how RIP and similar
#   interior protocols usually work. A more realistic implementation
#   would have a single unconnected UDP socket for all incoming and
#   outgoing messages.
#
#   The advantages of TCP for this education simulation are:
#       * separates per-neighbor control code from overall routing
#       * easier detection when a neighbor shuts down or crashes
#       * reliable messages, no need to fragment larger tables


class DVRouter(object):
    """Implement RIP style protocol for Distance Vector simulation"""
    
    def __init__(self, config):
        """Configure but do not start anything just yet"""
        super().__init__()
        self.quiet = config.logLevel > log.INFO
        self.assignNames(config)
        self.initNet(config)
        self.initRouting(config)
        self.running = True
        
    def assignNames(self, config):
        """ Set router and domain name"""
        if config.routerName is not None:
            self.name = config.routerName
        else:
            self.name = socket.gethostname()
        if config.domainName is not None:
            self.domain = config.domainName
        else:
            self.chooseDomain()
        log.info("New DVRouter {} for domain {}".format(self.name, self.domain))        
        
    def chooseDomain(self):
        """Generate random domain name we route for"""
        self.domain = None
        # Pick random from file?
        try:
            f = open("domains.txt", "r")
            choices = f.readlines()
            # Want router with same name to pick same domain each run, but
            # random module and Python hash() are not deterministic.
            hash = hashlib.md5(self.name.encode('UTF-8')).hexdigest()
            idx = int("0x" + hash[-4:], 16) % len(choices)
            self.domain = choices[idx].strip()
            f.close()
        except (OSError, ):
            log.warning("Could not read domain from file domains.txt")
        # If not, mangle our router name
        if self.domain is None or len(self.domain) == 0:
            chars = list(self.name)
            chars.reverse()
            self.domain = ''.join(chars).upper()
    
    def initNet(self, config):
        """Create server socket"""
        # Want to be compatible with IPv4 or IPv6
        # Change default group?
        if config.mcast is not None:
            links.mcastGroup = config.mcast
        self.ipVersion = links.ipVersion()
        if self.ipVersion == 6:
            self.addrFamily = socket.AF_INET6
            anyAddr = "::"
        else:
            self.addrFamily = socket.AF_INET
            anyAddr = "0.0.0.0"
        # Do this at startup so no delay when first link established
        self.sock = socket.socket(self.addrFamily, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        # A timeout makes sockets play nicely with Ctrl-C
        self.sock.settimeout(10)
        self.sock.bind((anyAddr, DV_PORT))
        self.sock.listen(5)
        log.debug("Router socket {}".format(self.sock.getsockname()))
        
    def initRouting(self, config):
        """Set up routing data and params"""
        # The routing table starts with just us
        self.master = RouteTable()
        self.master.merge(self.name, self.baseCostTable(), 0)
        # Are we advertising other routes?
        if config.evilFile:
            self.addFixedRoutes(config.evilFile)
        # How often we update
        if config.beat is not None:
            self.beat = config.beat
        else:
            self.beat = 20
        # Requests for new neighbor
        self.requests = queue.Queue()
        # Each neighbor gets own thread
        self.neighbors = []
    
    def addFixedRoutes(self, fileName):
        """Add all destinations in file to master table"""
        try:
            f = open(fileName, "r")
            for line in f.readlines():
                if line.startswith("#"):
                    continue
                dest, cost = line.split(":")
                e = RouteEntry(dest.strip(), self.name, int(cost.strip()))
                self.master.insert(e)
            f.close()
            log.info("Read extra routes from {}".format(fileName))
        except (OSError, ValueError):
            log.warning("Cannot read routes from {}".format(fileName))
            return

    ##
    
    def clock(self):
        """For all routing-related timestamps, delays, etc"""
        return time.monotonic()

    def newLink(self, linkIP):
        """New neighbour detected"""
        log.info("New neighbour {}".format(linkIP))
        # Because link detection has own thread, just push onto queue
        # for later processing
        self.requests.put(linkIP)
    
    def establishLink(self, linkIP):
        """Set up routing connection to new neighbor"""
        # Only want one socket between each pair of routers, so rule
        # is that whoever has highest IP address connects to the other
        try:
            if self.ipAddress > linkIP:
                log.debug("Try active connect")
                sock = socket.socket(self.addrFamily, socket.SOCK_STREAM)
                sock.connect((linkIP, DV_PORT))
            else:
                log.debug("Waiting for connect")
                # Note: it is possible that if there two new links and we
                # are waiting for both, this accept will return the socket
                # for a different link! But since the new neighbor identifies
                # the other end by the socket, it doesn't matter, we just
                # end up creating them in a different order
                # (A suspicious router would check that connection from known link)
                sock, remote = self.sock.accept()
            log.debug("TCP socket to router {}".format(linkIP))
        except (OSError, ):
            log.warning("Cannot connect to router {}".format(linkIP))
            links.removeLink(linkIP)
            return
        # We're good
        listen = DVNeighbor(self, sock)
        self.neighbors.append(listen)
        listen.start()
    
    def drop(self, link):
        """Used by neighbor thread to notify us that a link has gone down"""
        # Do nothing when shutting down
        if not self.running:
            return
        # Don't remove from neighbors list, possible thread conflict.
        # The DV main loop will collect the neighbor within a few seconds
        log.warning("Lost connection to router {} ({})".format(link.neighborName, link.neighborAddr))
        # Delete route, or poison?
        self.master.delete(link.neighborName)
        #self.master.poison(link.neighborName)
        
    ##
    
    def baseCostTable(self):
        """Return default startup table"""
        table = CostTable()
        table.set(self.domain, 0)
        return table
    
    def currentCostTable(self, neighbor):
        """Return current routing cost table for transmission"""
        table = CostTable()
        for k in self.master.keys():
            table.set(k, self.master.cost(k))
        return table
        
    def calculateRoutes(self):
        """Recalc everything"""
        nChanges = 0
        for n in self.neighbors:
            costs = n.currentCosts()
            nChanges += self.master.merge(n.neighborName, costs)
        if nChanges > 0:
            log.info("{} changes to routing table".format(nChanges))
        
    ##
    
    def run(self):
        """DV main loop"""
        links.start(self)
        # Need host IP address on the interface being used by links
        # which we can get from the multicast channel
        self.ipAddress = links.mcastChannel.output.getsockname()[0]
        log.debug("Router own address {}".format(self.ipAddress))
        # Timing
        now = self.clock()
        nextBeat = now
        try:
            print("Router: {} Domain: {}".format(self.name, self.domain))
            while self.running:
                now = self.clock()
                # New links?
                while not self.requests.empty():
                    req = self.requests.get()
                    self.establishLink(req)
                # Time to update routing?
                if now > nextBeat:
                    log.info("Router beat")
                    self.calculateRoutes()
                    print("\nRouting table")
                    if self.quiet:
                        print(self.master.active())
                    else:
                        print(self.master)
                    nextBeat += self.beat
                # Idle
                time.sleep(1)
                self.gcNeighbors()
        except(KeyboardInterrupt, ) as e:
            log.info("DV router interrupt")
        self.stop()

    def gcNeighbors(self):
        """Remove any neighbors that have shut down"""
        idx = 0
        while idx < len(self.neighbors):
            if not self.neighbors[idx].running:
                log.debug("Garbage collect neighbor #{}".format(idx))
                thr = self.neighbors[idx]
                thr.join()
                del self.neighbors[idx]
            else:
                idx += 1
    
    def stop(self):
        """Shut everything down"""
        self.running = False
        log.debug("Shut down neighbor threads")
        for thr in self.neighbors:
            thr.running = False
        for thr in self.neighbors:
            thr.join()
        log.debug("Shut down links and router socket")
        links.stop()
        self.sock.close()

##

class DVNeighbor(threading.Thread):
    """Listen to one linked neighbor"""
    
    def __init__(self, router, tcpSocket):
        super().__init__()
        # Our boss
        self.router = router
        # The other end
        self.sock = tcpSocket
        self.neighborAddr = self.sock.getpeername()[0]
        self.neighborName = None
        # Most recently received
        self.latest = CostTable()
        # Ready to go
        self.running = True
    
    def run(self):
        # Handshake: exchange router names
        try:
            writeLine(self.sock, self.router.name)
            self.neighborName = readLine(self.sock).strip()
            print("New neighbor {} ({})".format(self.neighborName, self.neighborAddr))
        except (OSError, ):
            log.warning("Handshake failed neighbor {}".format(self.neighborAddr))
            self.running = False
        # Main loop: keep sending and reading costs
        nextBeat = 0    # so first exchange immediate
        while self.running:
            now = self.router.clock()
            if now > nextBeat:
                try:
                    self.sendTable()
                    # No lock, object assignment in Python is atomic (almost always).
                    self.latest = self.readTable()
                except (OSError, ValueError):
                    self.running = False
                # Add some jitter to timing so routers do not lockstep
                dt = self.router.beat * 0.1
                nextBeat = now + self.router.beat + random.uniform(-dt, dt)
            time.sleep(1)
        log.debug("End connection neighbor {}".format(self.neighborName))
        self.sock.close()
        # In case main router tries to use before noticing we have ended
        self.latest = CostTable()
        # Notify main router
        self.router.drop(self)
        # If the program is ending this does not matter, but if it's
        # just this neighbor, want the link layer to try and find another
        links.removeLink(self.neighborAddr)
    
    def sendTable(self):
        """Transmit current routing table to neighbor"""
        table = self.router.currentCostTable(self)
        # No exception handling: want main loop to catch
        writeLine(self.sock, "DV {}".format(self.router.name))
        for line in str(table).splitlines():
            writeLine(self.sock, line)
        writeLine(self.sock, "END")
        log.debug("Sent table to neighbor {}".format(self.neighborName))
    
    def readTable(self):
        """Read current cost table from neighbor"""
        table = CostTable()
        errText = "Error in routing cost table entry"
        # As for send, no exception handling
        header = readLine(self.sock)
        if header is None or not header.startswith("DV "):
            raise ValueError(errText)
        while True:
            line = readLine(self.sock)
            if line is None:
                raise ValueError(errText)
            elif line.startswith("END"):
                break
            else:
                domain, cost = line.split(":")
                domain = domain.strip()
                # DVRouter adds link cost, not us
                cost = int(cost)
                table.set(domain, cost)
        print("{} costs".format(self.neighborName))
        print(table)
        return table

    def currentCosts(self):
        """Most recent costs for this neighbor"""
        return deepcopy(self.latest)

####


def parseArgs(argv):
    """Return arguments object with various options for code generation"""
    parser = argparse.ArgumentParser(
            description="Distance Vector routing simulator")
    parser.add_argument("-help", action="help")
    
    parser.add_argument("-name", type=str, dest="routerName", action="store",
            default=None,
            help="Router identifier")
    parser.add_argument("-domain", type=str, dest="domainName", action="store",
            default=None,
            help="Domain for router")
    parser.add_argument("-beat", type=int, dest="beat", action="store",
            default=None,
            help="Time in seconds between routing messages")
    parser.add_argument("-evil", type=str, dest="evilFile", action="store",
            default=None,
            help="File of fake destination:cost to advertise")
    parser.add_argument("-mcast", type=str, dest="mcast", action="store",
            default=None,
            help="Multicast group for link creation")

    parser.add_argument("-debug", dest="logLevel", action="store_const", const=log.DEBUG,
            default=log.INFO,
            help="Lots of detail and DEBUG level log messages")
    parser.add_argument("-quiet", "-q", dest="logLevel", action="store_const", const=log.WARNING,
            default=log.INFO,
            help="Less detail, only warning or error log messages")

    args = parser.parse_args(argv)
    return args

def main(argv):
    # Setup
    cliArgs = parseArgs(argv[1:])
    log.basicConfig(format="%(levelname)s %(message)s", datefmt="%H:%M:%S",
                    level=cliArgs.logLevel)
    # Make sure not in lockstep with other instances
    random.seed(time.perf_counter())
    # Go
    router = DVRouter(cliArgs)
    router.run()
    # ...
    log.info("End program")

if __name__ == "__main__":
    main(sys.argv)

