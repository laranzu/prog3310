
/** Link creation for routing simulator.
 *
 *  This class provides a multicast group protocol for programs
 *  to self-configure into subnets with one or more simulated
 *  point to point links, and functions for the actual routing
 *  protocol to get the available links.
 * 
 *  Routing program should call
 *      Links.start(the-routing-object)
 *      ...
 *      Links.stop()
 *  where the-routing-object is a delegate that handles new links.
 *  See the LinkDelegate spec later in code.
 * 
 *  There is no configuration needed other than a multicast group address.
 *  Each host exchanges messages to randomly distribute connections
 *  across all participating hosts.
 * 
 *  The protocol has three messages:
 *     JOIN        Sent by newly created node, and by nodes that
 *                 want more links. For an interesting simulation
 *                 really want more than one link per node.
 *     LINK <ip>   Response to JOIN, offer to establish link to node at <ip>
 *     LACK <ip>   Acknowledgement of LINK offer from <ip>
 * 
 *  Nodes that receive JOIN wait a random delay before sending a LINK
 *  offer, with delay increased by number of links already established.
 *  This distributes links over nodes more evenly.
 * 
 *  A LINK does not have to be acknowledged. A node that has already
 *  acknowledged an offer from another node can just not send a LACK.
 * 
 *  Written by Hugh Fisher, ANU, 2026
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
*/
package JDV;

import java.util.logging.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;

import static JDV.ProgramLogger.log;

import JDV.MCastChannel;

// Used as a singleton, not expecting to create more than one

public class Links {

    /** The object passed to Links.start() */
    public interface LinkDelegate {
        void newLink(InetAddress senderAddress);
    }

    /**  Network config */
    static String       mcastGroup = "224.0.0.70";
    //static String       mcastGroup = "ff15::3310";
    static int          mcastPort = 3310;
    static MCastChannel mcastChan;

    /**  Forming links */
    // Minimum number links we would like to have
    static int      preferNumLinks = 2;
    // Initial time between sending JOIN requests, millisecs
    static int      joinDelay = 4000;
    static final    int QUEUE_SIZE = 64;
    static LinkDelegate         delegate;
    static ArrayBlockingQueue   messageQ;

    /** Thread control */
    static boolean  running;
    static Thread   listen;
    static Thread   join;

    //****  Utility

    /** Want monotonic relative times, milliseconds */
    static long clock()
    {
        return System.nanoTime() / 1000000;
    }

    //****  Handle incoming messages

    static class Listener implements Runnable {
        private MCastChannel        chan;
        private ArrayBlockingQueue  messageQ;
        private LinkDelegate        delegate;

        Listener(MCastChannel mcastChan, ArrayBlockingQueue messages,
                    LinkDelegate linkDelegate)
        {
            this.chan = mcastChan;
            this.messageQ = messages;
            this.delegate = linkDelegate;
        }

        public void run()
        {
            DatagramPacket  packet;

            log.fine(String.format("Start link listener %s",
                        this.chan.address.toString()));
            while (Links.running && ! Thread.currentThread().isInterrupted()) {
                try {
                    packet = chan.recv();
                    if (packet == null)
                        continue;
                    log.fine(String.format("RECV %d bytes from %s",
                            packet.getLength(), packet.getAddress().toString()));
                } catch (Exception e) {
                    log.warning(String.format("Link Listener ERR %s", e.toString()));
                    Thread.currentThread().interrupt();
                }
            }
            log.fine("End link listener");
        }
    }

    /** Start link protocol */
    static void start(LinkDelegate programDelegate)
            throws UnknownHostException, IOException
    {
        log.info("Start link creation");
        mcastChan = new MCastChannel(mcastGroup, mcastPort);
        delegate = programDelegate;
        messageQ = new ArrayBlockingQueue(QUEUE_SIZE);
        // Threads
        running = true;
        listen = new Thread(new Listener(mcastChan, messageQ, programDelegate));
        listen.start();
    }

    /** And stop */
    static void stop()
    {
        log.fine("Stop Links threads");
        running = false;
        try {
            listen.join();
        } catch (InterruptedException e) {
            log.fine("Links interrupted?");
        }

        mcastChan.close();
        log.info("Link creation shutdown");
    }

    /** For testing */

    public static void main(String[] args)
    {
        log.setLevel(Level.FINE);
        try {
            Links.start(null);
            Thread.sleep(60 * 1000);
            Links.stop();
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }

}
