
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

import javax.net.ssl.*;


public class SSLClient {

    //  Default hostname and port that client will contact
    static String   webHost = "www.anu.edu.au";
    static int      webPort = 443;

    /** Read input until empty line, send as request */

    protected static void buildRequest(Socket sock)
        throws IOException
    {
        BufferedReader      input;
        String              line;

        input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            line = input.readLine();
            if (line == null)
                break;
            // Send line *then* check if it was empty
            sendRequest(sock, line);
            if (line.length() == 0)
                break;
        }
    }

    /** Just print whatever the server sends us */

    protected static void printResponse(Socket sock)
    {

    }

    /** Connect securely to host. Send a request, print response */

    protected static void inputLoop(String host, int port)
        throws IOException
    {
        Socket              sock;
        String              line, reply;
        InetSocketAddress   remote;

        // Set up SSL
        sock = new Socket(host, port);
        remote = (InetSocketAddress) sock.getRemoteSocketAddress();
        System.out.printf("Client connected to %s %d\n",
                            remote.getAddress().getHostAddress(), remote.getPort());
        // Read and send input lines
        buildRequest(sock);
        // Print the response
        printResponse(sock);
        //
        sock.close();
    }


    /** HTTP request header */

    protected static void sendRequest(Socket sock, String request)
        throws IOException
    {
        request += "\r\n";
        sock.getOutputStream().write(request.getBytes("UTF-8"));
    }

    /** HTTP reply header, or content if text */

    protected static String readLine(Socket sock)
        throws IOException
    {
        ByteArrayOutputStream inData = new ByteArrayOutputStream();
        int     ch;
        String  txt;

        while (true) {
            ch = sock.getInputStream().read();
            if (ch < 0) {
                if (inData.size() > 0)
                    break;
                else
                    return null;
            }
            inData.write((byte)ch);
            if (ch == (int)'\n')
                break;
        }
        txt = new String(inData.toByteArray(), 0, inData.size(), "UTF-8");
        return txt;
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
