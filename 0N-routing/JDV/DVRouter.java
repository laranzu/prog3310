
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
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static JDV.ProgramLogger.log;

public class DVRouter {

    static String   routerName;
    static String   domainName;
    static int      beat;
    static String   evilFile;
    static String   mcastGroup;
    static Level    logLevel;
    static boolean  quiet;

    /** Set router and domain name */
    protected static void assignNames()
            throws UnknownHostException
    {
        if (routerName == null)
            routerName = InetAddress.getLocalHost().getHostName();
        if (domainName == null)
            chooseDomain();
        log.fine(String.format("New DVRouter %s for domain %s",
                    routerName, domainName));
    }

    /** Generate random domain name we route for */
    protected static void chooseDomain()
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
            hash = md.digest(routerName.getBytes("UTF-8"));
            n = hash.length;
            idx = (hash[n - 4] << 24) | (hash[n - 3] << 16) |
                    (hash[n - 2] << 8) | (hash[n - 1]);
            idx = Math.abs(idx) % choices.size();
            domainName = choices.get(idx).strip();
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warning("Could not read domain from file domains.txt");
        }
        // If not, mangle our router name
        if (domainName == null || domainName.length() == 0) {
            chars = new StringBuilder(routerName);
            chars.reverse();
            domainName = chars.toString().toUpperCase();
        }
    }

    /** CLI arguments. Wish Java had Python style argparse in std lib */
    protected static void parseArgs(String[] args)
    {
        int     idx;
        String  arg;

        // Defaults
        beat = 20;
        logLevel = Level.INFO;

        idx = 0;
        while (idx < args.length) {
            arg = args[idx];
            if (arg.equals("-name")) {
                idx += 1;
                routerName = args[idx];
            } else if (arg.equals("-domain")) {
                idx += 1;
                domainName = args[idx];
            } else if (arg.equals("-debug")) {
                logLevel = Level.FINE;
            } else if (arg.equals("-quiet") || arg.equals("-q")) {
                logLevel = Level.WARNING;
            } else {
                log.warning(String.format("Unrecognised CLI arg: %s", arg));
            }
            idx += 1;
        }
        // Apply misc settings
        quiet = logLevel.intValue() > Level.INFO.intValue();
        log.setLevel(logLevel);
    }

    //****

    public static void main(String[] args)
    {
        try {
            parseArgs(args);
            assignNames();
        } catch (Exception e) {
            log.info("Exception in DVRouter.main");
        } finally {
            log.info("End program");
        }
    }
}
