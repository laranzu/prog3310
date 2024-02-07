
/** TCP echo server program for ANU COMP3310.
 *
 *  Run with
 *      java TcpServer [ IP addr ] [ port ]
 *
 *  Written by Hugh Fisher u9011925, ANU, 2024
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
 */


import java.io.*;
import java.net.*;


public class TcpServer {

    // IP address and port
    static String   serviceHost = "0.0.0.0";
    static int      servicePort = 3310;

    /** Accept client connections on given host and port */

    protected static void clientLoop(String host, int port)
        throws UnknownHostException, IOException, SocketException
    {
        ServerSocket        serverSock;
        Socket              client;
        InetSocketAddress   remote;

        // Create TCP socket, for server bound to given port and host.
        // Second arg is the limit on established TCP connections that have not yet
        // been accepted. (Not the number of connections.) A really busy server
        // might need to increase this but for now, don't worry about it.
        serverSock = new ServerSocket(port, 5, InetAddress.getByName(host));
        // Servers should set this option which allows them to be re-run immediately.
        // If you don't, you may have to wait a few minutes before restarting the server.
        serverSock.setReuseAddress(true);
        System.out.printf("Created server socket for %s %d\n",
                            serverSock.getInetAddress().getHostAddress(),
                            serverSock.getLocalPort());
        while (true) {
            // A TCP server is different to UDP. Instead of packets,
            // we get connection requests from clients.
            try {
                client = serverSock.accept();
            } catch (IOException e) {
                // If something goes wrong with the network, we will stop
                System.out.println(e.toString());
                break;
            }
            remote = (InetSocketAddress) client.getRemoteSocketAddress();
            System.out.printf("Accepted client connection from %s %d\n",
                            remote.getAddress().getHostAddress(), remote.getPort());
            // Each connection accepted creates a new socket for that
            // particular client. Use for requests and replies.
            //serverLoop(client);
            // We don't get back here until the client session ends
            client.close();
        }
        System.out.println("Close server socket");
        serverSock.close();
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
            clientLoop(serviceHost, servicePort);
            System.out.println("Done.");
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }

}
