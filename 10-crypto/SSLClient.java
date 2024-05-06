
/** SSL web connection program for ANU COMP3310.
 *
 *  Run with
 *      java SSLClient [ IP addr ] [ port ]
 *
 *  Reads and sends input lines from terminal until blank line.
 *  (In other words, a HTTP request.) Then reads and prints
 *  lines from server until closed.

 *  Written by Hugh Fisher u9011925, ANU, 2024
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
 */


import java.io.*;
import java.net.*;
import java.security.cert.*;

import javax.net.SocketFactory;
import javax.net.ssl.*;


public class SSLClient {

    //  Default hostname and port that client will contact
    static String   webHost = "www.anu.edu.au";
    static int      webPort = 443;


    /** Return new SSL socket to hostName:port */

    protected static SSLSocket openSSL(String hostName, int port)
        throws IOException
    {
        SSLSocket           sslSock;
        SocketFactory       maker;
        InetSocketAddress   remote;
        SSLSession          session;

        // In Java the underlying socket is created automatically
        // by the old style factory object that we have to use.
        maker = SSLSocketFactory.getDefault();
        sslSock = (SSLSocket)maker.createSocket(hostName, port);
        // Already connected for us, so initial secure handshake
        sslSock.startHandshake();
        session = sslSock.getSession();
        System.out.printf("Version: %s\n", session.getProtocol());
        System.out.printf("Cipher:  %s\n", session.getCipherSuite());
        System.out.println("Certificate:");
        for (Certificate cert : session.getPeerCertificates()) {
            System.out.print(cert.toString());
            // Just print the peer cert, not entire chain
            break;
        }
        remote = (InetSocketAddress) sslSock.getRemoteSocketAddress();
        System.out.printf("Client connected to %s %d\n",
                            remote.getAddress().getHostAddress(), remote.getPort());
        return sslSock;
    }

    /** Read input until empty line, send as request */

    protected static void buildRequest(Socket sock)
        throws IOException
    {
        BufferedReader  input;
        String          line;

        input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            line = input.readLine();
            if (line == null)
                break;
            // Send line *then* check if it was empty
            SockLine.writeLine(sock, line);
            if (line.length() == 0)
                break;
        }
    }

    /** Just print whatever the server sends us */

    protected static void printResponse(Socket sock)
        throws IOException
    {
        String  line;

        while (true) {
            line = SockLine.readLine(sock);
            if (line == null)
                break;
            // Response has line ending already
            System.out.print(line);
        }
    }

    /** Connect securely to host. Send a request, print response */

    protected static void inputLoop(String host, int port)
        throws IOException
    {
        Socket  sock;

        // Set up SSL
        sock = openSSL(host, port);
        // Read and send input lines
        buildRequest(sock);
        // Print the response
        printResponse(sock);
        //
        sock.close();
    }


    /** Handle command line arguments. */

    protected static void processArgs(String[] args)
    {
        //  This program has only two CLI arguments, and we know the order.
        //  For any program with more than two args, use a loop or package.
        if (args.length > 0) {
            webHost = args[0];
            if (args.length > 1) {
                webPort = Integer.parseInt(args[1]);
            }
        }
    }

    public static void main(String[] args)
    {
        try {
            processArgs(args);
            inputLoop(webHost, webPort);
            System.out.println("Done.");
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }

}
