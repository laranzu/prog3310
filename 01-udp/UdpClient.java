
/** UDP echo client program for ANU COMP3310.
 *
 *  This is the other half of a client-server architecture.
 *  The client knows the server, and makes requests to get a response.
 *
 *  Run with
 *      java UdpClient [ IP addr ] [ port ]
 *
 *  Written by H Fisher, ANU, 2024
 *  This code may be freely copied and modified
 */


import java.io.*;
import java.net.*;


public class UdpClient {

    //  IP address and port that client will contact
    static String   serviceHost = "127.0.0.1";
    static int      servicePort = 3310;

    // Our maximum UDP data size, in bytes.
    // Absolute maximum for UDP would be 64K, but reliability goes down a lot
    // for packets larger than 1K or so, and more than 8K is unlikely.
    static final int    MSG_MAX = 1024;

    // Maximum time to wait for a reply, in millisecs
    static final int    TIMEOUT = 4000;


    /** Read input until EOF. Send as request to host, print response */

    protected static void inputLoop(String host, int port)
        throws IOException
    {
        DatagramSocket      sock;
        BufferedReader      input;
        String              line;
        InetSocketAddress   remote;

        // Create an Internet UDP socket
        sock = new DatagramSocket();
        // This client will only connect to a single server, so we can set the peer
        // address, ie the remote server, for this socket just once.
        sock.connect(new InetSocketAddress(host, port));
        remote = (InetSocketAddress) sock.getRemoteSocketAddress();
        System.out.printf("Client created socket to %s %d\n",
                            remote.getAddress().getHostAddress(), remote.getPort());
        // UDP, like IP, is not a reliable protocol so we can't guarantee packets get
        // there, and in fact we can't even be sure that there is a server! This is
        // the maximum time to wait for a reply. (Also the maximum time to wait when
        // trying to send, but that usually is not a problem.)
        sock.setSoTimeout(TIMEOUT);
        // Now keep reading lines and sending them
        input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            line = input.readLine();
            if (line == null)
                break;
        }
        System.out.println("Client close");
        sock.close();
    }


    /** Handle command line arguments. */

    protected static void processArgs(String[] args)
    {
        //  This program has only two CLI arguments, and we know the order.
        //  For any program with more than two args, use a loop or package.
        if (args.length > 0) {
            serviceHost = args[0];
            if (args.length > 1) {
                servicePort = Integer.parseInt(args[1]);
            }
        }
    }

    public static void main(String[] args)
    {
        try {
            processArgs(args);
            inputLoop(serviceHost, servicePort);
            System.out.println("Done.");
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }

}
