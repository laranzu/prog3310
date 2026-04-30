
/** Name of domain, name of router, cost */

package JDV;

public class RouteEntry
        implements Comparable<RouteEntry> {
    public String domain;
    public String router;
    public int    cost;

    public RouteEntry(String domain, String router, int cost)
    {
        this.domain = domain;
        this.router = router;
        this.cost = cost;
    }

    public String toString()
    {
        return String.format("  %-24s: %-16s: %2d",
                this.domain, this.router, this.cost);
    }

    public int compareTo(RouteEntry other)
    {
        return Integer.compare(this.cost, other.cost);
    }
}
