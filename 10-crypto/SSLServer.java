
/** Very very very simple SSL web server program for ANU COMP3310.
 *
 *  Run with
 *      java SSLServer [ port ]
 *
 *  Written by Hugh Fisher u9011925, ANU, 2024
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
 */


import java.io.*;
import java.net.*;
import java.security.cert.*;

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;

public class SSLServer {

    // Assume running unprivileged
    static int      servicePort = 3310;


    /** Return new server socket */

    protected static SSLServerSocket serverSSL(int port)
        throws IOException
    {
        SSLServerSocket         sslSock;
        ServerSocketFactory     maker;

        // In Java the underlying socket is created automatically
        // by the old style factory object that we have to use.
        maker = SSLServerSocketFactory.getDefault();
        sslSock = (SSLServerSocket)maker.createServerSocket(port);
        sslSock.setReuseAddress(true);
        // TODO set up certificates somehow
        return sslSock;
    }

    /** Testing HTTP only */

    protected static ServerSocket serverPlain(int port)
        throws IOException
    {
        ServerSocket sock;

        sock = ServerSocketFactory.getDefault().createServerSocket(port);
        sock.setReuseAddress(true);
        return sock;
    }

    /** Accept client connections on given port */

    protected static void clientLoop(int port)
        throws IOException, SocketException
    {
        ServerSocket        serverSock;
        Socket              client;
        InetSocketAddress   remote;

        // Set up passive socket
        serverSock = serverSSL(port);
        //serverSock = serverPlain(port); // Use this for plain http test 
        System.out.printf("Created SSL server socket for %s %d\n",
                            serverSock.getInetAddress().getHostAddress(),
                            serverSock.getLocalPort());
        while (true) {
            try {
                client = serverSock.accept();
            } catch (IOException e) {
                // If something goes wrong with the network, we will stop
                System.out.printf("%s in clientLoop\n", e.toString());
                break;
            }
            remote = (InetSocketAddress) client.getRemoteSocketAddress();
            System.out.printf("Accepted client connection from %s %d\n",
                            remote.getAddress().getHostAddress(), remote.getPort());
            // And pretend to be web server
            singleRequest(client);
        }
        System.out.println("Close server socket");
        serverSock.close();
    }

    /** Single HTTP request for a single client */

    protected static void singleRequest(Socket sock)
        throws IOException
    {
        String request, header;

        try {
            // We only handle GET / and don't care about request headers
            request = SockLine.readLine(sock);
            while (true) {
                header = SockLine.readLine(sock);
                if (header == null || header.length() == 0)
                    break;
            }
            System.out.printf("Server received %s", request);
            // Respond
            if (request.startsWith("GET / HTTP")) {
                SockLine.writeLine(sock, "HTTP/1.0 200 OK");
                SockLine.writeLine(sock, "Content-Type: text/html");
                SockLine.writeLine(sock, "");
                SockLine.writeLine(sock, "<html><head><title>Hello</title></head><body><h1>Hello World</h1></body></html>");
            } else {
                SockLine.writeLine(sock, "HTTP/1.0 404 Server only has / resource");
                SockLine.writeLine(sock, "");
            }
        } catch (IOException e) {
            // Try not to crash if the client does something wrong
            System.out.printf("%s in singleRequest\n", e.toString());
        }
        System.out.println("Close client connection");
        sock.close();
    }


    /** Handle command line argument. */

    protected static void processArgs(String[] args)
    {
        if (args.length > 0) {
            servicePort = Integer.parseInt(args[1]);
        }
    }

    public static void main(String[] args)
    {
        try {
            processArgs(args);
            clientLoop(servicePort);
            System.out.println("Done.");
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }

}
