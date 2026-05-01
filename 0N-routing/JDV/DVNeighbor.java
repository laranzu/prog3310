/** Handle one link for DV routing simulator
 * 
 *  Written by Hugh Fisher, ANU, 2026
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
*/
package JDV;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.net.*;

import static JDV.ProgramLogger.log;

class DVNeighbor extends Thread {

    DVRouter    router;
    Socket      sock;
    InetSocketAddress neighborAddr;
    String      neighborName;
    CostTable   latest;
    boolean     running;
    

    public DVNeighbor(DVRouter router, Socket tcpSocket)
    {
        super();
        // Our boss
        this.router = router;
        // The other end
        this.sock = tcpSocket;
        this.neighborAddr = (InetSocketAddress)this.sock.getRemoteSocketAddress();
        this.neighborName = "";
        // Most recently received
        this.latest = new CostTable();
        // Ready to go
        this.running = true;
    }
    
    public void run()
    {
        long    now, nextBeat;
        double  dt;
        
        // Handshake: exchange router names
        try {
            SockLine.writeLine(this.sock, this.router.name);
            this.neighborName = SockLine.readLine(this.sock).strip();
            System.out.println(String.format("New neighbor %s (%s)",
                            this.neighborName, this.neighborAddr));
        } catch (IOException e) {
            log.warning(String.format("Handshake failed neighbor %s",
                        this.neighborAddr));
            this.running = false;
        }
        // Main loop: keep sending and reading costs
        nextBeat = 0;   // so first exchange immediate
        try {
            while (this.running) {
                now = this.router.clock();
                if (now > nextBeat) {
                        this.sendTable();
                        // No lock, object assignment is atomic?
                        this.latest = this.readTable();
                    // Add some jitter to timing so routers do not lockstep
                    dt = this.router.beat * 0.1;
                    nextBeat = now + this.router.beat + (long)((Math.random() - 0.5) * dt);
                }
                Thread.sleep(1000);
            }
            log.fine(String.format("End connection %s", this.neighborName));
        this.sock.close();
        } catch (IOException | InterruptedException e) {
            this.running = false;
        }
        // In case main router tries to use before noticing we have ended
        this.latest = new CostTable();
        // Notify main router
        this.router.drop(this);
        // If the program is ending this does not matter, but if it's
        // just this neighbor, want the link layer to try and find another
        Links.removeLink(Links.linkAddr(this.neighborAddr));
    }
    
    /** Transmit current routing table to neighbor */
    void sendTable()
            throws IOException
    {
        CostTable table;
        
        table = this.router.currentCostTable(this);
        // No exception handling: want main loop to catch
        SockLine.writeLine(this.sock, "DV " + this.router.name);
        for (String line : table.toString().split("\n")) {
            SockLine.writeLine(this.sock, line);
        }
        SockLine.writeLine(this.sock, "END");
        log.fine(String.format("Sent table to neighbor %s", this.neighborName));
    }
    
    /** Read current cost table from neighbor */
    CostTable readTable()
            throws IOException
    {
        CostTable   table;
        String      errText, header, line;
        String[]    fields;
        String      domain;
        int         cost;
        
        table = new CostTable();
        errText = "Error in routing cost table entry";
        // As for send, no exception handling
        header = SockLine.readLine(this.sock);
        if (header == null || ! header.startsWith("DV "))
            throw new RuntimeException(errText);
        while (true) {
            line = SockLine.readLine(this.sock);
            if (line == null)
                throw new RuntimeException(errText);
            else if (line.startsWith("END"))
                break;
            else {
                fields = line.split(":");
                domain = fields[0].strip();
                // DVRouter adds link cost, not us
                cost = Integer.parseInt(fields[1].strip());
                table.put(domain, cost);
            }
        }
        System.out.println(String.format("%s costs", this.neighborName));
        System.out.println(table);
        return table;
    }
    
    /** Most recent costs for this neighbor */
    CostTable currentCosts()
    {
        return (CostTable)this.latest.clone();
    }
}

