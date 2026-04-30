
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

    // Group address and interface
    public InetSocketAddress    address;
    protected NetworkInterface  iface;
    // Address (non-multicast) we send as
    protected SocketAddress     srcAddr;

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
        this.output = new MulticastSocket();
        this.output.joinGroup(this.address, this.iface);
        this.output.connect(this.address);
        // Own source address for detecting loopbacks
        this.srcAddr = this.output.getLocalSocketAddress();
        log.fine(String.format("Source address on send %s", this.srcAddr.toString()));

        log.fine("MCastChannel sockets created");
     }

    public void send(String message)
            throws IOException
    {
        byte[] data;
        DatagramPacket packet;

        data = message.getBytes("UTF-8");
        packet = new DatagramPacket(data, data.length);
        this.output.send(packet);
    }

    public DatagramPacket recv()
            throws IOException
    {
        DatagramPacket packet;

        packet = new DatagramPacket(new byte[PKT_SIZE], PKT_SIZE);
        try {
            this.input.receive(packet);
            return packet;
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    protected void close()
    {
        this.input.close();
        this.output.close();
        log.fine("Closed channel");
    }

}
