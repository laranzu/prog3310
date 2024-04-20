
/** Message digest demonstration code for COMP3310.
 *  Calculate and print hash (signature) for a file,
 *  or show available hash algorithms.
 * 
 *  Run with
 *      java HashSig file [ algorithm ]
 *  or
 *      java HashSig -list
 * 
 *  Written by Hugh Fisher u9011925, ANU, 2024
 */

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;


public class HashSig {

    static String   fileName = null;
    static String   algo = "MD5";

    protected static String hexDigest(byte[] binary)
    {
        // I really wish this was standard in Java
        StringBuilder s = new StringBuilder();
        for (byte b : binary) {
            s.append(String.format("%02x", b));
        }
        return s.toString();
    }

    protected static void hash(String infile, String algo)
        throws NoSuchAlgorithmException, IOException
    {
        // Print hash signature for infile
        MessageDigest   digest;
        byte[]          sig;
        DataInputStream src;
        byte[]          block = new byte[1024];
        int             nBytes;

        // Hash algorithm
        digest = MessageDigest.getInstance(algo);
        // Data to sign
        src = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(infile))));
        while (true) {
            nBytes = src.read(block);
            if (nBytes <= 0)
                break;
            digest.update(block, 0, nBytes);
        }
        src.close();
        sig = digest.digest();
        System.out.println(hexDigest(sig));
    }

    protected static void available()
    {
        // Print list of hash algorithms Java can provide
        ArrayList<String> algos;

        System.out.println("Algorithm names that work on this system");
        algos = new ArrayList<>(Security.getAlgorithms("MessageDigest"));
        Collections.sort(algos);
        System.out.println(algos);
    }

    protected static void reminder()
    {
        System.out.println("Needs command line arguments");
        System.out.println("HashSig filename [ algorithm ]");
        System.out.println("HashSig -list");
    }

    public static void main(String[] args)
    {
        try {
            if (args.length == 0 || args.length > 2) {
                reminder();
            } else if (args[0].equals("-list")) {
                available();
            } else {
                fileName = args[0];
                if (args.length > 1)
                    algo = args[1];
                hash(fileName, algo);
            }
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }
}
