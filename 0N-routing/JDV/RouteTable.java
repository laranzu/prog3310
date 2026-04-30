
/** Complete set of possible routes on a single router.
 * 
 *  Written by Hugh Fisher, ANU, 2026
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
*/
package JDV;

import java.util.*;
import java.util.logging.*;

import static JDV.ProgramLogger.log;

public class RouteTable extends Hashtable<String, ArrayList<RouteEntry>> {

    // Maximum cost. RIP uses 15, but for simulation a bigger number is more dramatic
    public final int INFINITY = 65535;

    public RouteTable()
    {
        super();
    }

    /** Produce nicely formatted and sorted table */

    public String toString()
    {
        List<String> destKeys;
        StringBuilder txt;

        destKeys = new ArrayList<>(this.keySet());
        Collections.sort(destKeys);
        txt = new StringBuilder();
        for (String k : destKeys) {
            txt.append(String.format("%s\n", k, this.get(k).toString()));
        }
        return txt.toString();
    }

    /** Return lowest cost for destination */

    public int cost(String domain)
    {
        if (this.contains(domain))
            return this.get(domain).get(0).cost;
        else
            return INFINITY;
    }

    /** Return routing table containing only active (first) link each destination */

    public RouteTable active()
    {
        RouteTable table;
        List<String> destKeys;

        table = new RouteTable();
        destKeys = new ArrayList<>(this.keySet());
        Collections.sort(destKeys);
        for (String dest : destKeys) {
            table.put(dest, new ArrayList<RouteEntry>(this.get(dest).subList(0, 1)));
        }
        return table;
    }

    /** Entries for each destination ordered by cost */

    public void resort()
    {
        for (String k : this.keySet()) {
            Collections.sort(this.get(k));
        }
    }

    public static void main(String[] args)
    {
        RouteTable rt;

        rt = new RouteTable();
        //ct.put("foo", 1);
        //ct.put("bar", 2);
        //ct.put("supercalifragilistic", 4);
        System.out.println(rt);
    }
}
