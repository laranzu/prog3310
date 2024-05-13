
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

class SockLine {

    /** Write single line with CR LF */

    public static void writeLine(Socket sock, String txt)
        throws IOException
    {
        txt = txt + "\r\n";
        sock.getOutputStream().write(txt.getBytes("UTF-8"));
    }

    /** Read single line terminated by \r\n, or null if closed. */

    public static String readLine(Socket sock)
        throws IOException
    {
        // Read as bytes. Only convert to UTF-8 when we have entire line.
        // A memory mapped output stream is the easiest way I know
        // to store a varying length sequence of bytes.
        ByteArrayOutputStream inData = new ByteArrayOutputStream();
        byte[]  data;
        int     ch, count;
        String  txt;

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
            inData.write((byte)ch);
            // This comparison always works with UTF-8 because high bytes
            // of multi byte characters have at least bit 7 set
            if (ch == (int)'\n')
                break;
        }
        // Get bytes so we can check for and remove CR LF at end
        data = inData.toByteArray();
        count = inData.size();
        if (count > 0 && data[count - 1] == (int)'\n')
            count -= 1;
        if (count > 0 && data[count - 1] == (int)'\r')
            count -= 1;
        txt = new String(data, 0, count, "UTF-8");
        return txt;
    }

}
