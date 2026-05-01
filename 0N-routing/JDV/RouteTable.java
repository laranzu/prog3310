
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
        ArrayList<RouteEntry> routes;
        StringBuilder txt;

        destKeys = new ArrayList<>(this.keySet());
        Collections.sort(destKeys);
        txt = new StringBuilder();
        for (String k : destKeys) {
            routes = this.get(k);
            if (routes.size() == 0) {
                txt.append(String.format("%-24s: Unreachable\n", k));
            } else {
                for (RouteEntry e : routes)
                    txt.append(String.format("  %s\n", e.toString()));
            }
        }
        return txt.toString();
    }

    /** Return lowest cost for destination */

    public int cost(String domain)
    {
        if (this.containsKey(domain))
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

    /** Insert routing entry into table, return True if changed */

    public boolean insert(RouteEntry entry)
    {
        boolean found;

        if (this.containsKey(entry.domain)) {
            // Existing entry?
            found = false;
            for (RouteEntry e : this.get(entry.domain)) {
                if (e.domain.equals(entry.domain) && e.router.equals(entry.router)) {
                    if (e.cost == entry.cost)
                        // No change, can stop here
                        return false;
                    else {
                        e.cost = entry.cost;
                        log.fine(String.format(
                                "Change cost dest %s link %s = %d",
                                entry.domain, entry.router, entry.cost));
                        found = true;
                        break;
                    }
                }
            }
            if (! found) {
                // New entry for existing domain
                this.get(entry.domain).add(entry);
                log.fine(String.format(
                        "New route to dest %s link %s cost %d",
                        entry.domain, entry.router, entry.cost));
            }
            // Route table always ordered
            Collections.sort(this.get(entry.domain));
        } else {
            // New destination
            this.put(entry.domain, new ArrayList<RouteEntry>(List.of(entry)));
            log.fine(String.format("New dest %s link %s cost %d",
                        entry.domain, entry.router, entry.cost));
        }
        return true;
    }

    /** Insert all the entries in cost table from router, return number changes */

    public int merge(String router, CostTable costs, int hops)
    {
        int n, c;
        RouteEntry e;

        n = 0;
        for (String dest : costs.keySet()) {
            // Don't try to count past infinity :-)
            c = costs.get(dest);
            if (c < INFINITY)
                c += hops;
            e = new RouteEntry(dest, router, c);
            if (this.insert(e))
                n += 1;
        }
        return n;
    }

    /** Mark all routes through router as infinite cost */

    public void poison(String router)
    {
        log.info(String.format("Poison router %s", router));
        for (String dest : this.keySet()) {
            for (RouteEntry e : this.get(dest)) {
                if (e.router.equals(router)) {
                    log.fine(String.format("Set %s via %s to INFINITY",
                            dest, router));
                    e.cost = INFINITY;
                }
            }
        }
        this.resort();
    }

    /** Remove all entries through router */

    public void delete(String router)
    {
        ArrayList<String> doomed;
        ArrayList<RouteEntry> routes;
        int idx;

        log.info(String.format("Delete routes through %s", router));
        // Deleting from dict while iterating does not work, need to remember
        doomed = new ArrayList<>();
        for (String dest : this.keySet()) {
            routes = this.get(dest);
            idx = 0;
            while (idx < routes.size()) {
                if (routes.get(idx).router.equals(router)) {
                    log.fine(String.format("Delete route to %s through %s",
                                    dest, router));
                    routes.remove(idx);
                } else
                    idx += 1;
            }
            // Empty list?
            if (routes.size() == 0) {
                log.fine(String.format("No routes remaining for {}", dest));
                doomed.add(dest);
            }
        }
        // Now can delete empty
        for (String d : doomed)
            this.remove(d);
    }

    /** Testing */

    public static void main(String[] args)
    {
        RouteTable rt;
        RouteEntry foo, bar, buzz;

        log.setLevel(Level.FINE);
        rt = new RouteTable();
        foo = new RouteEntry("foo", "me", 1);
        bar = new RouteEntry("bar", "you", 2);
        buzz = new RouteEntry("bar", "buzz", 1);
        rt.insert(foo);
        rt.insert(bar);
        rt.insert(buzz);
        System.out.println(rt);
        System.out.println(rt.active());
        foo = new RouteEntry("foo", "me", 4);
        rt.insert(foo);
        System.out.println(rt);
        rt.delete("buzz");
        System.out.println(rt);
    }
}
