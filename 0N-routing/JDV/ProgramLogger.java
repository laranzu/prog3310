
//  My preferred logging setup
//
//  Written by Hugh (Hugo) Fisher, 2023
//  Released under Creative Commons CC0 Public Domain Dedication
//  This code may be freely copied and modified for any purpose

package JDV;

import java.util.logging.*;
import java.util.Properties;

/**
 * Single logger object used throughout program
 */

public class ProgramLogger {
    
    static final Logger log = Logger.getLogger("JDV");

    static {
        // Single line console format
        Properties p = new Properties(System.getProperties());
        p.setProperty("java.util.logging.SimpleFormatter.format", "%4$s %1$tT %5$s %n");
        System.setProperties(p);

        // I want stderr and stdout in one place
        System.setErr(System.out);
        
        // Need to replace default handler to see debug messages
        log.setLevel(Level.INFO);
        log.setUseParentHandlers(false);
        Handler myHandler = new ConsoleHandler();
        myHandler.setFormatter(new SimpleFormatter());
        // Handlers have their own level, and if it is higher than the
        // logger level messages still get filtered. So set the handler
        // to print everything, logger level will control output.
        myHandler.setLevel(Level.ALL);
        log.addHandler(myHandler);
    }
}
