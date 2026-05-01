
""" Routing tables for simulator.
    
    Provides two types
    
    CostTable is what gets transmitted over individual links between routers.
    
    RouteTable is complete set of possible routes on a single router.
"""

#   Written by Hugh Fisher, ANU, 2026
#   Released under Creative Commons CC0 Public Domain Dedication
#   This code may be freely copied and modified for any purpose

import copy
import logging as log


# Maximum cost. RIP uses 15, but for simulation a bigger number is more dramatic
INFINITY = 65535

class CostTable(dict):
    """ Simple destination : hops table.
    
        Keys are destination names (strings)
        Values are hop count
    """
    
    def __init__(self):
        super().__init__()
    
    def set(self, dest, cost):
        """dest should be string, cost int"""
        self[dest] = cost
    
    # Defining __str__ means that print() is same as wire format
    def __str__(self):
        """Produce nicely formatted and sorted table"""
        txt = ""
        destKeys = self.keys()
        destKeys = sorted(destKeys, key=lambda dest: self[dest])
        for k in destKeys:
            txt += "  {:24}: {:2d}".format(k, self[k])
            txt += "\n"
        return txt

##

class RouteEntry(object):
    """Simple data carrier"""
    def __init__(self, domain, router, cost):
        """"Name of domain, name of router, cost"""
        self.domain = domain
        self.router = router
        self.cost   = cost

class RouteTable(dict):
    """Complete table of destinations and possible routes"""
    
    def __init__(self):
        super().__init__()
        
    def __str__(self):
        """Produce nicely formatted and sorted table"""
        txt = ""
        destKeys = sorted(self.keys())
        for domain in destKeys:
            routes = sorted(self[domain], key=lambda e: e.cost)
            if len(routes) == 0:
                text += "{:24}: Unreachable\n".format(domain)
            else:
                for e in routes:
                    txt += "  {:24}: {:16}: {:2d}\n".format(e.domain, e.router, e.cost)
        return txt
    
    def cost(self, domain):
        """Return lowest cost for destination"""
        if domain not in self:
            return INFINITY
        else:
            return self[domain][0].cost
    
    def active(self):
        """Return routing table containing only active (first) link each destination"""
        table = RouteTable()
        for dest in sorted(self.keys()):
            table[dest] = self[dest][0:1]
        return table
    
    def resort(self):
        """Entries for each destination ordered by cost"""
        for k in self.keys():
            self[k].sort(key=lambda e: e.cost)
    
    def insert(self, entry):
        """Insert routing entry into table, return True if changed"""
        if entry.domain in self:
            # Existing entry?
            for e in self[entry.domain]:
                if e.domain == entry.domain and e.router == entry.router:
                    if e.cost == entry.cost:
                        # No change, can stop here
                        return False
                    else:
                        e.cost = entry.cost
                        log.debug("Change cost dest {} link {} = {}".format(
                                    entry.domain, entry.router, entry.cost))
                        break
            else:
                # For loop did not break, new entry for existing destination
                self[entry.domain].append(entry)
                log.debug("New route to dest {} link {} cost {}".format(
                                entry.domain, entry.router, entry.cost))
            # Route table always ordered
            self[entry.domain].sort(key=lambda e: e.cost)
        else:
            # New destination
            self[entry.domain] = [entry, ]
            log.debug("New dest {} link {} cost {}".format(entry.domain, entry.router, entry.cost))
        return True
        
    def merge(self, router, costs, hops=1):
        """Insert all the entries in cost table from router, return number changes"""
        n = 0
        for dest in costs.keys():
            # Don't try to count past infinity :-)
            e = RouteEntry(dest, router, min(costs[dest] + hops, INFINITY))
            if self.insert(e):
                n += 1
        self.resort()
        return n
    
    def poison(self, router):
        """Mark all routes through router as infinite cost"""
        log.info("Poison router {}".format(router))
        for dest in self.keys():
            for e in self[dest]:
                if e.router == router:
                    log.debug("Set {} via {} to INFINITY".format(dest, router))
                    e.cost = INFINITY
        self.resort()

    def delete(self, router):
        """Remove all entries through router"""
        log.info("Delete routes through {}".format(router))
        # Deleting from dict while iterating does not work, need to remember
        doomed = []
        for dest in self.keys():
            routes = self[dest]
            idx = 0
            while idx < len(routes):
                if routes[idx].router == router:
                    log.debug("Delete route to {} through {}".format(dest, router))
                    del routes[idx]
                else:
                    idx += 1
            # Empty list?
            if len(routes) == 0:
                log.debug("No routes remaining for {}".format(dest))
                doomed.append(dest)
        # Now can delete empty lists
        for d in doomed:
            del self[d]

####

if __name__ == "__main__":
    log.basicConfig(format="%(levelname)s %(message)s", datefmt="%H:%M:%S", level=log.DEBUG)
    rt = RouteTable()
    foo = RouteEntry("foo", "me", 1)
    bar = RouteEntry("bar", "you", 2)
    buzz = RouteEntry("bar", "buzz", 1)
    rt.insert(foo)
    rt.insert(bar)
    rt.insert(buzz)
    print(rt)
    print(rt.active())
    foo = RouteEntry("foo", "me", 4)
    rt.insert(foo)
    print(rt)
    rt.delete("buzz")
    print(rt)
