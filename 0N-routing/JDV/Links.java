
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
 *  Can run stand alone for testing from parent dir
 *      java JDV.Links
 *
 *  There is no configuration needed other than a multicast group address.
 *  Each host exchanges messages to randomly distribute connections
 *  across all participating hosts.
 * 
 *  The protocol has two messages:
 *     JOIN        Sent by newly created node, and by nodes that
 *                 want more links. For an interesting simulation
 *                 really want more than one link per node.
 *     LINK <ip>   Response to JOIN, offer to establish link to
 *                 node at <ip>, listen on TCP socket. Joiner
 *                 acknowledges offer by connecting over TCP.
 * 
 *  Nodes that receive JOIN wait a random delay before sending a LINK
 *  offer, with delay increased by number of links already established.
 *  This distributes links over nodes more evenly.
 * 
 *  A LINK does not have to be acknowledged. A node that has already
 *  acknowledged an offer from another node can just not connect.
 * 
 *  In version 1 there was an extra message, Link ACK, and the link
 *  layer did not open a TCP socket, instead just passing the IP
 *  address up to the router. This could lead to asymmetric links
 *  and other inconsistencies if a LACK got lost or the router did
 *  not open a connection.
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
        void newLink(Socket tcpSocket);
    }

    // Java really needs typedef
    static class InetAddrQueue extends ArrayBlockingQueue<InetSocketAddress> {
        public InetAddrQueue(int capacity) { super(capacity); }
    };

    /**  Network config */
    // The multicast group address for link formation.
    // This is NOT the address used by the routing protocol
    // NOTE: would like to use a 239. address, but those are blocked
    // in CompSci labs. 224. link local does work. Thanks Felix
    static String       mcastGroup = "224.0.0.70";
    // Opposite for IPv6 in CompSci: site-specific transient works, but not link-local
    // NOTE: the multicast links protocol works on IPv6, but the current lab PCS
    // do not have IPv6 addresses assigned so the router TCP sockets do not work :-(
    //static String       mcastGroup = "ff15::3310";
    static int          mcastPort = 3310;
    // On non-lab PCs may need to specify interface
    static NetworkInterface mcastInterface;
    static MCastChannel     mcastChan;

    /** TCP port and passive socket for actual links */
    static int          ptpPort = 5252;
    static ServerSocket ptpSock;

    /**  Forming links */
    // Minimum number links we would like to have
    static int      preferNumLinks = 2;
    // Initial time between sending JOIN requests, millisecs
    static int      joinDelay = 4000;
    static final    int QUEUE_SIZE = 64;
    // Object that wants to know about links
    static LinkDelegate delegate;

    /** The links */
    static Map<String, Socket> activeLinks;

    /** Thread control */
    static volatile boolean running;
    static InetAddrQueue    messageQ;
    static ExecutorService  scheduler;

    //****  Utility

    /** Want monotonic relative times, milliseconds */
    static long clock()
    {
        return System.nanoTime() / 1000000;
    }

    /** Identify links by IP address as string, no port */
    static String linkAddr(SocketAddress nodeAddress)
    {
        return ((InetSocketAddress)nodeAddress).getHostString();
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

    static synchronized void addLink(Socket linkSocket)
    {
        String ipAddress = linkAddr(linkSocket.getRemoteSocketAddress());
        if (activeLinks.putIfAbsent(ipAddress, linkSocket) != null) {
            log.info(String.format("PTP link #%d to %s",
                    activeLinks.size(), ipAddress));
        }
    }

    static synchronized void removeLink(Socket linkSocket)
    {
        String k;

        // Socket might have been closed, so cannot lookup remote address
        k = null;
        for (Map.Entry<String, Socket> entry : activeLinks.entrySet()) {
            if (entry.getValue() == linkSocket)
                k = entry.getKey();
        }
        if (k != null) {
            activeLinks.remove(k);
        }
    }

    /** Return list of established point to point links */
    static synchronized ArrayList<Socket> active()
    {
        return new ArrayList<Socket>(activeLinks.values());
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
                        this.group.address.getHostString()));
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
            if (Links.activeLinks.containsKey(linkAddr(sender)))
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
            Socket linkSock;

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
                        ! Links.activeLinks.containsKey(linkID)) {
                log.fine(String.format("Try active link to %s", linkID));
                try {
                    linkSock = new Socket(linkID, Links.ptpPort);
                    Links.addLink(linkSock);
                    if (this.delegate != null)
                        this.delegate.newLink(linkSock);
                } catch (IOException e) {
                    log.warning(String.format("Could not connect to link offer from %s", linkID));
                }
            } else {
                log.fine(String.format("Ignore link from %s", sender.toString()));
            }
        }
    }

    /** Send JOIN and LINK requests */

    static class Joiner implements Runnable {
        private MCastChannel  group;
        private InetAddrQueue messages;
        private LinkDelegate  delegate;

        Joiner(MCastChannel mcastChan,
                InetAddrQueue messageQueue,
                LinkDelegate linkDelegate)
        {
            this.group = mcastChan;
            this.messages = messageQueue;
            this.delegate = linkDelegate;
        }

        public void run()
        {
            InetSocketAddress request;
            long nextJoin, now;

            log.fine(String.format("Start link joiner %s",
                        this.group.address.getHostString()));
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
            Socket linkSock;

            // Random delay, plus extra for each existing link. This sleep
            // also means we only respond to one JOIN at a time
            Thread.sleep((long)(Math.random() * Links.joinDelay) +
                            Links.activeLinks.size() * Links.joinDelay);
            log.fine(String.format("Offer link to %s", request.toString()));
            this.group.send(String.format("LINK %s", linkAddr(request)));
            try {
                log.fine("Wait for PTP connect");
                linkSock = ptpSock.accept();
                log.fine("Accepted PTP");
                Links.addLink(linkSock);
                if (this.delegate != null)
                    this.delegate.newLink(linkSock);
            } catch (IOException e) {
                log.warning("No response to LINK offer");
            }
        }
    }

    //****      Main control


    /** Start link protocol */
    static void start(LinkDelegate programDelegate, ExecutorService threadPool)
            throws UnknownHostException, IOException
    {
        log.info("Start link creation");
        // Network sockets
        mcastChan = new MCastChannel(mcastGroup, mcastPort, mcastInterface);
        ptpSock = createPassive();
        // Out list of links
        activeLinks = new HashMap<String, Socket>();
        delegate = programDelegate;
        messageQ = new InetAddrQueue(QUEUE_SIZE);
        // Threads
        running = true;
        if (threadPool != null)
            scheduler = threadPool;
        else
            scheduler = Executors.newCachedThreadPool();
        scheduler.execute(new Listener(mcastChan, messageQ, programDelegate));
        scheduler.execute(new Joiner(mcastChan, messageQ, programDelegate));
    }

    static void start(LinkDelegate programDelegate)
            throws UnknownHostException, IOException
    {
        start(programDelegate, null);
    }

    static ServerSocket createPassive()
            throws UnknownHostException, IOException, SocketException
    {
        ServerSocket sock;
        String       anyAddr;

        // Create a TCP socket for incoming point to point
        if (ipVersion() == 6)
            anyAddr = "::";
        else
            anyAddr = "0.0.0.0";
        sock = new ServerSocket(ptpPort, 5, InetAddress.getByName(anyAddr));
        sock.setSoTimeout(joinDelay);
        sock.setReuseAddress(true);
        log.fine(String.format("Created passive PTP socket %s : %d",
                                sock.getInetAddress().getHostAddress(),
                                sock.getLocalPort()));
        return sock;
    }

    /** And stop */
    static void stop()
    {
        log.fine("Stop Links threads");
        running = false;
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Don't care
        }
        log.fine("Close Links sockets");
        try {
            ptpSock.close();
            for (Socket ptp : activeLinks.values()) {
                ptp.close();
            }
        } catch (IOException e) {
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
