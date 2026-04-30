
# COMP3310 Routing Example / Exercise

This is an example and/or exercise for COMP3310 Computer Networks,
demonstrating the basics of distance vector routing.

Designed for Comp Sci computer labs, where all machines share a
LAN. All students run the program, which pretends to be a router
for an entire domain and establishes "point to point" links to
a subset of the other running copies.

Once the virtual links are established, the program then runs
a distance vector routing protocol (RIP) and prints out lots of
messages about links being up or down, changing costs, etc.

## Source files

    routeTable.py
    dvRouter.py

Routing table data structures and the main routing program.

    ../domains.txt

List of imaginary domains/subnets/areas for the routers.

    links.py

Use a multicast group address for self configuring mesh of hosts,
each of which establishes links to a few others.

    sockLine.py

Utility code for reading and writing lines of text over TCP socket.

    mcast.py

Low level code for multicast sockets.


