
/** Main program for Distance Vector routing simulator.
 * 
 *  Run as a package from parent directory
 *      python -m PyDV.dvRouter [ args ]
 * 
 *  CLI args
 *      -name NAME      Router identifier, defaults hostname
 *      -domain NAME    Router domain, default to random from domains.txt
 *      -beat SECS      How often to send router messages
 *      -evil FILE      Advertise routes listed in FILE as well as ourself
 * 
 *      -mcast MCAST    Use different multicast address for link creation
 * 
 *      -debug          Lots and lots of internal operation detail
 *      -quiet          Only warnings and above
 * 
 *  Uses American spelling for neighbor because primary reference is
 *  Interconnections: Second Edition by Radia Perlman
 * 
 *  Written by Hugh Fisher, ANU, 2026
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
*/
package JDV;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static JDV.ProgramLogger.log;

/** Design: TCP or UDP?
 *  This routing simulator creates a TCP connection and control thread
 *  between each pair of neighbors, which is not how RIP and similar
 *  interior protocols usually work. A more realistic implementation
 *  would have a single unconnected UDP socket for all incoming and
 *  outgoing messages.
 * 
 *  The advantages of TCP for this education simulation are:
 *      - separates per-neighbor control code from overall routing
 *      - easier detection when a neighbor shuts down or crashes
 *      - reliable messages, no need to fragment larger tables
 */

public class DVRouter
        implements Runnable, Links.LinkDelegate {

    // The port for router messages
    static final int DV_PORT = 5252;

    String  name;
    String  domain;
    int     beat;
    String  evilFile;
    String  mcastGroup;
    String  ipAddress;
    Level   logLevel;
    boolean quiet;

    boolean         running;
    ServerSocket    sock;
    RouteTable      master;
    ArrayBlockingQueue<String> requests;
    ArrayList<DVNeighbor> neighbors;

    //****      Setup       ****/
    
    /** Configure but do not start anything just yet */
    public DVRouter(String[] args)
            throws UnknownHostException, IOException
    {
        this.parseArgs(args);
        this.assignNames();
        this.initNet();
        this.initRouting();
        this.running = true;
    }

    /** Set router and domain name */
    void assignNames()
            throws UnknownHostException
    {
        if (this.name == null)
            this.name = InetAddress.getLocalHost().getHostName();
        if (this.domain == null)
            this.chooseDomain();
        log.fine(String.format("New DVRouter %s for domain %s",
                    name, domain));
    }

    /** Generate random domain name we route for */
    void chooseDomain()
    {
        List<String>    choices;
        StringBuilder   chars;
        MessageDigest   md;
        byte[]          hash;
        int             n, idx;

        // Pick random from file?
        try {
            choices = Files.readAllLines(Path.of("domains.txt"),
                            StandardCharsets.UTF_8);
            // Want router with same name to pick same domain each run, but
            // random and object hash() are not deterministic.
            md = MessageDigest.getInstance("MD5");
            hash = md.digest(this.name.getBytes("UTF-8"));
            n = hash.length;
            idx = (hash[n - 4] << 24) | (hash[n - 3] << 16) |
                    (hash[n - 2] << 8) | (hash[n - 1]);
            idx = Math.abs(idx) % choices.size();
            this.domain = choices.get(idx).strip();
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warning("Could not read domain from file domains.txt");
        }
        // If not, mangle our router name
        if (this.domain == null || this.domain.length() == 0) {
            chars = new StringBuilder(this.name);
            chars.reverse();
            this.domain = chars.toString().toUpperCase();
        }
    }

    /** Create server socket */
    void initNet()
            throws UnknownHostException, IOException, SocketException
    {
        int ipVersion;
        String anyAddr;

        // Change group?
        if (this.mcastGroup != null)
            Links.mcastGroup = this.mcastGroup;

        // Want to be compatible with IPv4 or IPv6
        ipVersion = Links.ipVersion();
        if (ipVersion == 6)
            anyAddr = "::";
        else
            anyAddr = "0.0.0.0";
        // Do this at startup so no delay when first link established
        this.sock = new ServerSocket(DV_PORT, 5, InetAddress.getByName(anyAddr));
        this.sock.setReuseAddress(true);
        log.fine(String.format("Router socket %s : %d",
                        this.sock.getInetAddress().getHostAddress().toString(),
                        this.sock.getLocalPort()));
    }

    /** Set up routing data and params */
    void initRouting()
    {
        // The routing table starts with just us
        this.master = new RouteTable();
        this.master.merge(this.name, this.baseCostTable(), 0);
        // Are we advertising other routes?
        if (evilFile != null)
            this.addFixedRoutes(evilFile);
        // Beat time is in secs, but Java threads use millisecs
        this.beat *= 1000;
        // Requests for new neighbor
        this.requests = new ArrayBlockingQueue<String>(8);
        // Each neighbor gets own thread
        this.neighbors = new ArrayList<DVNeighbor>();
    }
    
    /** Add all destinations in file to master table */
    void addFixedRoutes(String fileName)
    {
        List<String> routes;
        String[]    fields;
        String      dest;
        int         cost;
        RouteEntry  e;
        
        try {
            routes = Files.readAllLines(Path.of(fileName),
                            StandardCharsets.UTF_8);
            for (String line : routes) {
                if (line.startsWith("#"))
                    continue;
                fields = line.split(":");
                dest = fields[0];
                cost = Integer.parseInt(fields[1].strip());
                e = new RouteEntry(dest.strip(), this.name, cost);
                this.master.insert(e);
            }
            log.info(String.format("Read extra routes from %s", fileName));
        } catch (IOException | ArrayIndexOutOfBoundsException err) {
            log.warning(String.format("Cannot read routes from %s", fileName));
        }
    }

    //****      Link management     ****/
    
    /** For all routing-related timestamps, delays, etc */
    long clock()
    {
        return System.nanoTime() / 1000000;
    }
    
    /** New neighbour detected */
    public void newLink(String linkIP)
    {
        log.info(String.format("New neighbour %s", linkIP));
        // Because link detection has own thread, just push onto
        // queue for later processing
        try {
            this.requests.add(linkIP);
        } catch (IllegalStateException e) {
            log.warning("Requests queue full, drop link");
            Links.removeLink(linkIP);
        }
    }
    
    /** Set up routing connection to new neighbor */
    void establishLink(String linkIP)
    {
        Socket      sock;
        DVNeighbor  listen;
        
        // Only want one socket between each pair of routers, so rule
        // is that whoever has highest IP address connects to the other
        try {
            if (this.ipAddress.compareTo(linkIP) > 0) {
                log.fine("Try active connect");
                sock = new Socket(linkIP, DV_PORT);
            } else {
                log.fine("Waiting for connect");
                // Note: it is possible that if there two new links and we
                // are waiting for both, this accept will return the socket
                // for a different link! But since the new neighbor identifies
                // the other end by the socket, it doesn't matter, we just
                // end up creating them in a different order
                // (A suspicious router would check that connection from known link)
                sock = this.sock.accept();
            }
            log.fine(String.format("TCP socket to router %s", linkIP));
        } catch (IOException e) {
            log.warning(String.format("Cannot connect to router %s", linkIP));
            Links.removeLink(linkIP);
            return;
        }
        // We're good
       listen = new DVNeighbor(this, sock);
       this.neighbors.add(listen);
       listen.start();
    }
    
    /** Used by neighbor thread to notify us that a link has gone down */
    void drop(DVNeighbor link)
    {
        // Do nothing when shutting down
        if (! this.running)
            return;
        // Don't remove from neighbors list, possible thread conflict.
        // The DV main loop will collect the neighbor within a few seconds
        log.warning(String.format("Lost connection to router %s (%s)",
                    link.neighborName, link.neighborAddr));
        // Delete route, or poison?
        this.master.delete(link.neighborName);
        //this.master.poison(link.neighborName);
    }
    
    //****      Tables      ****/
    
    /** Return default startup table */
    CostTable baseCostTable()
    {
        CostTable table;

        table = new CostTable();
        table.put(this.domain, 0);
        return table;
    }
    
    /** Return current routing cost table for transmission */
    CostTable currentCostTable(DVNeighbor neighbor)
    {
        CostTable table;
        
        table = new CostTable();
        for (String k : this.master.keySet()) {
            table.put(k, this.master.cost(k));
        }
        return table;
    }
    
    /** Recalc everything */
    void calculateRoutes()
    {
        int nChanges;
        
        nChanges = 0;
        // TODO Each neighbor
        if (nChanges > 0)
            log.info(String.format("%d changes to routing table", nChanges));
    }

    //****          Main program            ****/

    /** DV main loop */
    public void run()
    {
        long    now, nextBeat;
        String  req;
        
        try {
            Links.start(this);
            // Need host IP address on the interface being used by links
            // which we can get from the multicast channel
            this.ipAddress = Links.mcastChan.output.getLocalAddress().getHostAddress().toString();
            log.fine(String.format("Router own address %s", this.ipAddress));
            // Timing
            now = this.clock();
            nextBeat = now;
            System.out.println(String.format("Router: %s Domain: %s",
                            this.name, this.domain));
            while (this.running && ! Thread.currentThread().isInterrupted()) {
                now = this.clock();
                // New links?
                while (this.requests.size() > 0) {
                    req = this.requests.take();
                    this.establishLink(req);
                }
                // Time to update routing?
                if (now > nextBeat) {
                    log.info("Router beat");
                    this.calculateRoutes();
                    System.out.println("\nRouting table");
                    if (this.quiet)
                        System.out.println(this.master.active());
                    else
                        System.out.println(this.master);
                    nextBeat += this.beat;
                }
                // Idle
                Thread.sleep(1 * 1000);
            }
        } catch (IOException | InterruptedException e) {
            log.info("DV router interrupt");
        }
        try {
            this.stop();
        } catch (Exception e) {
            // Don't care
        }
    }
    
    /** Shut everything down */
    void stop()
            throws IOException
    {
        this.running = false;
        log.fine("Shut down neighbor threads");
        log.fine("Shut down links and router socket");
        Links.stop();
        this.sock.close();
    }

    /** CLI arguments. Wish Java had Python style argparse in std lib */
    void parseArgs(String[] args)
    {
        int     idx;
        String  arg;

        // Defaults
        this.beat = 20;
        this.logLevel = Level.INFO;

        idx = 0;
        while (idx < args.length) {
            arg = args[idx];
            if (arg.equals("-name")) {
                idx += 1;
                this.name = args[idx];
            } else if (arg.equals("-domain")) {
                idx += 1;
                this.domain = args[idx];
            } else if (arg.equals("-beat")) {
                idx += 1;
                this.beat = Integer.parseInt(args[idx]);
            } else if (arg.equals("-evil")) {
                idx += 1;
                this.evilFile = args[idx];
            } else if (arg.equals("-debug")) {
                this.logLevel = Level.FINE;
            } else if (arg.equals("-quiet") || arg.equals("-q")) {
                this.logLevel = Level.WARNING;
            } else {
                log.warning(String.format("Unrecognised CLI arg: %s", arg));
            }
            idx += 1;
        }
        // Apply misc settings
        this.quiet = this.logLevel.intValue() > Level.INFO.intValue();
        log.setLevel(this.logLevel);
    }

    public static void main(String[] args)
    {
        DVRouter router;

        try {
            router = new DVRouter(args);
            router.run();
        } catch (Exception e) {
            log.info(String.format("Exception in DVRouter.main: %s", e.toString()));
        } finally {
            log.info("End program");
        }
    }
}
