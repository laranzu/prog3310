
/** TCP utility code for ANU COMP3310.
 *  Read and write lines of text over TCP socket, handling
 *  EOL and decoding/encoding UTF-8. Nothing very complex
 *  but avoids copying and pasting over and over again.
 * 
 *  There is no limit on the size of a line.
 *
 *  Written by Hugh Fisher u9011925, ANU, 2024
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;

class SockLine {

    /** Write single line with LF */

    public static void writeLine(Socket sock, String txt)
        throws IOException
    {
        txt = txt + "\n";
        sock.getOutputStream().write(txt.getBytes("UTF-8"));
    }

    /** Read single line terminated by \n, or null if closed. */

    public static String readLine(Socket sock)
        throws IOException
    {
        // Read as bytes. Only convert to UTF-8 when we have entire line.
        ArrayList<Byte> inData = new ArrayList<>();
        int     ch;

        while (true) {
            ch = sock.getInputStream().read();
            if (ch < 0) {
                // Socket closed. If we have any data it is an incomplete
                // line, otherwise immediately return null
            if (inData.size() > 0)
                break;
            else
                return null;
            }
            inData.add((byte)ch);

        }
        return "";
    }

}
