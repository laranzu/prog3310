
/** Python UDP echo server program for ANU COMP3310.
 *
 *  This is one half of a traditional Internet client-server architecture.
 *  As a server this program is expected to run (more or less) all the
 *  time, and handle requests from any client that connects.
 *
 *  Run with
 *      Java UdpServer [ IP addr ] [ port ]
 * 
 *  Written by H Fisher, ANU, 2024
 *  Creative Commons CC-BY-4.0 license
 */

import java.io.*;
import java.net.*;


public class UdpServer {

    //  IP address and port that server reads requests from.
    //  Client needs to know these to connect.
    static String   serviceHost = "127.0.0.1";
    static int      servicePort = 3310;

    //  Maximum client request size, in bytes
    static final int    MSG_SIZE = 16;


    /** Run echo service on given host and port */

    protected static void serverLoop(String host, int port)
        throws IOException
    {
        DatagramSocket  sock;
        DatagramPacket  request;
        String          message, reply;

        // Create and Internet UDP socket and set the address and port that we read requests from
        sock = new DatagramSocket(new InetSocketAddress(host, port));
        System.out.printf("Server created socket for %s %d\n",
                            sock.getLocalAddress().getHostAddress(), sock.getLocalPort());
        // And now read and respond forever
        while (true) {
            try {
                // In UDP we usually don't have a permanent connection to the client, so we
                // read a network packet (up to limit) AND address of where it came from.
                request = new DatagramPacket(new byte[MSG_SIZE], MSG_SIZE);
                sock.receive(request);
                // The data is just bytes. We hope it is a UTF-8 string, the Internet standard
                // for sending text, but Java uses a different format internally.
                // Internet programs should decode network data as soon as received.
                try {
                    message = new String(request.getData(), 0, request.getLength(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // Not UTF-8 :-(
                    System.out.println("Unable to decode UTF-8 string");
                    message = "????";
                }
                // Decide what to do
                if (message.equals("it")) {
                    System.out.println("We refuse to reply");
                } else if (message.equals("ni")) {
                    System.out.println("Sending multiple replies");
                    for (int i = 0; i < 3; i += 1) {
                        reply = "Ni!";
                        sendReply(sock, reply, request);
                    }
                } else {
                    reply = "ACK " + message;
                    System.out.printf("Server sending reply %s\n", reply);
                    sendReply(sock, reply, request);
                }
            } catch (IOException e) {
                break;
            }
        }
        System.out.println("Server close");
        sock.close();
    }

    /** Send complete reply to client */

    protected static void sendReply(DatagramSocket sock, String message, DatagramPacket request)
        throws UnsupportedEncodingException, IOException
    {
        byte[]          outData;
        DatagramPacket  reply;

        // UDP packets are raw bytes so text should be UTF-8, the "wire format".
        // Encoding is usually done just before sending so the rest of the program
        // doesn't need to worry about what packets look like.
        outData = message.getBytes("UTF-8");
        // Create a new packet, same address as original request
        reply = new DatagramPacket(outData, outData.length, request.getSocketAddress());
        // and send
        sock.send(reply);
    }


    /** Handle command line arguments. */

    protected static void processArgs(String[] args)
    {
        //  This program has only two CLI arguments, and we know the order.
        //  For any program with more than two args, use a loop or library.
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
            serverLoop(serviceHost, servicePort);
            System.out.println("Done.");
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }

}
