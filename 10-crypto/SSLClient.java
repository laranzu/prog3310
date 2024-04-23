
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

    /** Read input until EOF. Send as request to host, print response */

    protected static void inputLoop(String host, int port)
        throws IOException
    {
        Socket              sock;
        BufferedReader      input;
        String              line, reply;
        InetSocketAddress   remote;

        // Create TCP socket, connected to a single host
        sock = new Socket(host, port);
        remote = (InetSocketAddress) sock.getRemoteSocketAddress();
        System.out.printf("Client connected to %s %d\n",
                            remote.getAddress().getHostAddress(), remote.getPort());
        // Keep reading lines and sending them
        input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            line = input.readLine();
            if (line == null)
                break;
            sendRequest(sock, line);
            readReply(sock);
        }
        System.out.println("Client close");
        // Tell the server we are done
        SockLine.writeLine(sock, "BYE");
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
