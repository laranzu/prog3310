
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

import java.util.*;
import java.util.logging.*;

import static JDV.ProgramLogger.log;

public class DVRouter {

    static String   routerName;
    static String   domainName;
    static int      beat;
    static String   evilFile;
    static String   mcastGroup;
    static Level    logLevel;
    static boolean  quiet;

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
            if (arg.equals("-debug")) {
                logLevel = Level.FINE;
            } else if (arg.equals("-quiet") || arg.equals("-q")) {
                logLevel = Level.WARNING;
            }
            idx += 1;
        }
        // Apply misc settings
        quiet = logLevel.intValue() > Level.INFO.intValue();
        log.setLevel(logLevel);
    }

    public static void main(String[] args)
    {
        parseArgs(args);
        log.info("End program");
    }
}
