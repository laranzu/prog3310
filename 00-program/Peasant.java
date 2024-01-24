
/** COMP3310 Tute 0 programming warmup exercise.
 *  This is the Java version of the first program.
 *  Compile with
 *      javac Peasant.java
 *  Run with
 *      java Peasant
 *
 *  Written by H Fisher, ANU, 2024
 *  Creative Commons CC-BY-4.0 license
 */

import java.io.*;

public class Peasant {

    static String name = null;

    protected static void inputLoop()
        throws IOException
    {
        BufferedReader input;
        String line, response;

        // Read and echo input until EOF
        input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            line = input.readLine();
            // Java Reader just returns null on EOF, not an exception
            if (line == null)
                break;
            if (name == null)
                response = line;
            else
                response = String.format("%s: %s", name, line);
            System.out.println(response);
        }
        input.close();
    }

    protected static void processArgs(String[] args)
    {
        // Handle command line arguments, just one for this program
        if (args.length > 0) {
            name = args[0];
        }
    }

    public static void main(String[] args)
    {
        // Java, unlike Python, insists we handle all exceptions
        try {
            processArgs(args);
            inputLoop();
            System.out.println("Done.");
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);   
        }
    }

}
