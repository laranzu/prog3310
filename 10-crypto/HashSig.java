
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
import java.security.MessageDigest;
import java.security.Security;


public class HashSig {

    static String   algo = "";

    protected static void available()
    {
        ArrayList<String> algos;

        System.out.println("Algorithm names that work on this system");
        algos = new ArrayList<>(Security.getAlgorithms("MessageDigest"));
        Collections.sort(algos);
        System.out.println(algos);
    }

    public static void main(String[] args)
    {
        try {
            if (args[0].equals("-list")) {
                available();
            } else {
                ;
            }
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }
}
