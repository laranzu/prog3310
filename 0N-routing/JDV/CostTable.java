/** Simple destination : hops table for routing simulator.
 *  Transmitted over individual links between routers.
 * 
 *  Written by Hugh Fisher, ANU, 2026
 *  Released under Creative Commons CC0 Public Domain Dedication
 *  This code may be freely copied and modified for any purpose
*/
package JDV;

import java.util.*;
import java.util.logging.*;

import static JDV.ProgramLogger.log;

/** Keys are destination names (strings)
 *  Values are hop count
*/

public class CostTable extends Hashtable<String, Integer> {
    public CostTable()
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
            txt.append(String.format("  %-24s: %2d\n", k, this.get(k)));
        }
        return txt.toString();
    }
}
