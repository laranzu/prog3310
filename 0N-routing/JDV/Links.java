
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
 *  TODO : if a LACK gets lost, won't have symmetric link.
 * 
 *  Written by Hugh Fisher, ANU, 2026
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
*/
package JDV;

import java.util.*;
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
        void newLink(String senderAddress);
    }

    // Java really needs typedef
    static class InetAddrQueue extends ArrayBlockingQueue<InetSocketAddress> {
        public InetAddrQueue(int capacity) { super(capacity); }
    };

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
    // Object that wants to know about links
    static LinkDelegate delegate;

    /** The links */
    static ArrayList<String> activeLinks;

    /** Thread control */
    static boolean  running;
    static InetAddrQueue messageQ;
    static Thread   listen;
    static Thread   output;

    //****  Utility

    /** Want monotonic relative times, milliseconds */
    static long clock()
    {
        return System.nanoTime() / 1000000;
    }

    /** Identify links by IP address as string, no port */
    static String linkAddr(InetSocketAddress nodeAddress)
    {
        return nodeAddress.getHostString();
    }

    /** Are we IPv4 or 6? */
    static int ipVersion()
    {
        try {
            InetAddress a = InetAddress.getByName(mcastGroup);
            if (a instanceof Inet4Address)
                return 4;
            else if (a instanceof Inet6Address)
                return 6;
            else
                return 0;
        } catch (UnknownHostException e) {
            log.severe("Links: Cannot determine IP version");
            return -1;
        }
    }

    /** Thread safe access to links */

    static synchronized void addLink(String ipAddress)
    {
        if (! activeLinks.contains(ipAddress)) {
            activeLinks.add(ipAddress);
            log.info(String.format("PTP link #%d to %s",
                    activeLinks.size(), ipAddress));
        }
    }

    static synchronized void removeLink(String ipAddress)
    {
        if (activeLinks.remove(ipAddress))
            log.info(String.format("Remove link %s",
                    ipAddress));
    }

    /** Return list of established point to point links */
    static synchronized ArrayList<String> active()
    {
        return new ArrayList<String>(activeLinks);
    }

    //****  Internal threads

    /** Accept incoming messages, decide how to respond */

    static class Listener implements Runnable {
        private MCastChannel    group;
        private InetAddrQueue   messages;
        private LinkDelegate    delegate;

        Listener(MCastChannel mcastChan,
                    InetAddrQueue messageQueue,
                    LinkDelegate linkDelegate)
        {
            this.group = mcastChan;
            this.messages = messageQueue;
            this.delegate = linkDelegate;
        }
        
        public void run()
        {
            DatagramPacket  packet;
            String          msg;
            InetSocketAddress   sender;

            log.fine(String.format("Start link listener %s",
                        this.group.address.toString()));
            while (Links.running && ! Thread.currentThread().isInterrupted()) {
                try {
                    packet = group.recv();
                    if (packet == null)
                        continue; // Timeout
                    //Multicast loopback is (probably) on so we get copies of everything we send
                    sender = (InetSocketAddress)packet.getSocketAddress();
                    if (sender.equals(this.group.srcAddr))
                        continue;
                    // OK, what do we do?
                    msg = new String(packet.getData(), 0,
                                    packet.getLength(), "UTF-8");
                    log.fine(String.format("Received %s from %s",
                            msg, packet.getSocketAddress().toString()));
                    if (msg.startsWith("JOIN"))
                        this.doJoin(msg, sender);
                    else if (msg.startsWith("LINK"))
                        this.doLink(msg, sender);
                    else if (msg.startsWith("LACK"))
                        this.doAck(msg, sender);
                    else
                        log.warning(String.format("Link listener unknown message type: %s", msg));
                } catch (IOException e) {
                    log.severe(String.format("Links Listener error %s",
                                                e.toString()));
                    // Want other threads to stop as well
                    Links.running = false;
                    Thread.currentThread().interrupt();
                }
            }
            log.fine("End link listener");
        }

        void doJoin(String msg, InetSocketAddress sender)
        {
            // Already linked?
            if (Links.activeLinks.contains(linkAddr(sender)))
                return;
            // Delayed response, handled by joiner thread
            try {
                this.messages.add(sender);
            } catch (IllegalStateException e) {
                log.warning("Link queue full, drop message");
            }
        }

        void doLink(String msg, InetSocketAddress sender)
                throws IOException
        {
            String addr, linkID;

            // Meant for us?
            try {
                addr = msg.split(" ")[1].strip();
            } catch (ArrayIndexOutOfBoundsException e) {
                log.warning(String.format("No address in %s", msg));
                return;
            }
            if (! addr.equals(linkAddr(this.group.srcAddr)))
                return;
            // May already be linked, or someone else may have already
            // responded to our JOIN
            linkID = linkAddr(sender);
            if (Links.activeLinks.size() < Links.preferNumLinks &&
                        ! Links.activeLinks.contains(linkID)) {
                log.fine(String.format("Accept link from %s", linkID));
                Links.addLink(linkID);
                if (this.delegate != null)
                    this.delegate.newLink(linkID);
                this.group.send(String.format("LACK %s", linkID));
            } else {
                log.fine(String.format("Ignore link from %s", sender.toString()));
            }
        }
        
        void doAck(String msg, InetSocketAddress sender)
        {
            String addr, linkID;

            // Meant for us?
            addr = msg.split(" ")[1].strip();
            if (! addr.equals(linkAddr(this.group.srcAddr)))
                return;
            linkID = linkAddr(sender);
            // Must be in response to our offer, so always add
            Links.addLink(linkID);
            if (this.delegate != null)
                this.delegate.newLink(linkID);
            log.fine(String.format("Link ack from %s", sender.toString()));
        }
    }

    /** Send JOIN and LINK requests */

    static class Joiner implements Runnable {
        private MCastChannel  group;
        private InetAddrQueue messages;

        Joiner(MCastChannel mcastChan, InetAddrQueue messageQueue)
        {
            this.group = mcastChan;
            this.messages = messageQueue;
        }

        public void run()
        {
            InetSocketAddress request;
            long nextJoin, now;

            log.fine(String.format("Start link joiner %s",
                        this.group.address.toString()));
            // Initial request straight away
            nextJoin = Links.clock() - 1;
            while (Links.running && ! Thread.currentThread().isInterrupted()) {
                try {
                    // Join to process?
                    request = this.messages.poll(1, TimeUnit.SECONDS);
                    if (request != null) {
                        this.respondJoin(request);
                    }
                    // More links?
                    if (Links.activeLinks.size() < Links.preferNumLinks) {
                        now = Links.clock();
                        if (now > nextJoin) {
                            this.group.send("JOIN");
                            log.fine("Send JOIN");
                            nextJoin = now + Links.joinDelay;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    log.severe(String.format("Links Joiner error %s", e.toString()));
                    // Want other threads to stop as well
                    Links.running = false;
                    Thread.currentThread().interrupt();
                }
            }
            log.fine("End link joiner");
        }

        /** Delayed response to JOIN */
        void respondJoin(InetSocketAddress request)
                throws InterruptedException, IOException
        {
            // Random delay, plus extra for each existing link. This sleep
            // also means we only respond to one JOIN at a time
            Thread.sleep((long)(Math.random() * Links.joinDelay) +
                            Links.activeLinks.size() * Links.joinDelay);
            this.group.send(String.format("LINK %s", linkAddr(request)));
            log.fine(String.format("Offer link to %s", request.toString()));
        }
    }

    //****      Main control


    /** Start link protocol */
    static void start(LinkDelegate programDelegate)
            throws UnknownHostException, IOException
    {
        log.info("Start link creation");
        activeLinks = new ArrayList<>();
        mcastChan = new MCastChannel(mcastGroup, mcastPort);
        delegate = programDelegate;
        messageQ = new InetAddrQueue(QUEUE_SIZE);
        // Threads
        running = true;
        listen = new Thread(new Listener(mcastChan, messageQ, programDelegate));
        output = new Thread(new Joiner(mcastChan, messageQ));
        listen.start();
        output.start();
    }

    /** And stop */
    static void stop()
    {
        log.fine("Stop Links threads");
        running = false;
        try {
            output.join();
            listen.join();
        } catch (InterruptedException e) {
            // Don't care
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
        } catch (Exception e) {
            System.out.println(e.toString());
        } finally {
            Links.stop();
        }
    }

}
