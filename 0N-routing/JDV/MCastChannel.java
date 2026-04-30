
/** Multicast IP group channel.
 *
 *  Multicast is conceptually the same as UDP on a socket,
 *  but in practice it is much more complicated :-(
 *  Two sockets, one for sending and one for receiving,
 *  work best across multiple platforms; and there are
 *  differences between IPv4 and IPv6 group membership.
 *
 *  Written by Hugh Fisher, ANU, 2026
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
*/

package JDV;

import java.util.logging.*;
import java.io.*;
import java.net.*;

import static JDV.ProgramLogger.log;


public class MCastChannel {

    public static final int PKT_SIZE = 1024;

    public MulticastSocket  input;
    public MulticastSocket  output;

    protected InetSocketAddress address;
    protected NetworkInterface  iface;
    protected InetAddress       srcAddr;

    protected int   seqNo;

    /** Create socket pair and join group */

    MCastChannel(String ipAddress, int portNumber)
            throws UnknownHostException, IOException, SocketException
    {
        this.address  = new InetSocketAddress(ipAddress, portNumber);
        this.srcAddr  = null;
        this.seqNo    = 0;
        this.createSockets();
        log.info(String.format("Connected to group channel %s : %d",
                    this.address.getHostString(), this.address.getPort()));
    }

    /** Input and output sockets */

    protected void createSockets()
            throws UnknownHostException, IOException, SocketException
    {
        log.fine(String.format("Create input socket for %s : %d",
                    this.address.getHostString(), this.address.getPort()));
        this.input = new MulticastSocket(this.address.getPort());
        this.iface = NetworkInterface.getByInetAddress(this.address.getAddress());
        // Joining is so much easier in Java than in Python
        this.input.joinGroup(this.address, this.iface);
        this.input.setSoTimeout(1 * 1000);

        log.fine(String.format("Create output socket for %s : %d",
                    this.address.getHostString(), this.address.getPort()));
        this.output = new MulticastSocket(this.address.getPort());
        this.output.connect(this.address);

        // Own source address for detecting loopbacks
        this.srcAddr = this.output.getLocalAddress();

        log.fine("MCastChannel sockets created");
     }

    protected void close()
    {
        this.input.close();
        this.output.close();
        log.fine("Closed channel");
    }

    /** For testing, can create a channel? */

    public static void main(String[] args)
    {
        log.setLevel(Level.FINE);
        try {
            MCastChannel chan = new MCastChannel("224.0.0.70", 3310);
            log.info(String.format("Source address on send %s", chan.srcAddr.toString()));
            chan.close();
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }
}
