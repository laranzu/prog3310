
/** Main program for Distance Vector routing simulator.
 * 
 *  Run as a module from parent directory
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

public class DVRouter {

    // The port for router messages
    static final int DV_PORT = 5252;

    String   name;
    String   domain;
    int      beat;
    String   evilFile;
    String   mcastGroup;
    Level    logLevel;
    boolean  quiet;

    ServerSocket sock;
    RouteTable   master;

    public DVRouter(String[] args)
            throws UnknownHostException, IOException
    {
        this.parseArgs(args);
        this.assignNames();
        this.initNet();
        this.initRouting();
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
    }

    /** Return default startup table */
    CostTable baseCostTable()
    {
        CostTable table;

        table = new CostTable();
        table.put(this.domain, 0);
        return table;
    }

    //****          Main program            ****


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

    //****

    public static void main(String[] args)
    {
        DVRouter router;

        try {
            router = new DVRouter(args);
        } catch (Exception e) {
            log.info("Exception in DVRouter.main");
        } finally {
            log.info("End program");
        }
    }
}
