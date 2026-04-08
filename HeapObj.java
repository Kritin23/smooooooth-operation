import java`.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;



public class HeapObj {
    Unit alloc_site;

    HeapObj(Unit alloc_site) {
        this.alloc_site = alloc_site;
    }

    public String toString() {
        return "HeapObj allocated at: " + alloc_site;
    }

    @Override
    public int hashCode() {
        return alloc_site.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HeapObj) {
            HeapObj other = (HeapObj) o;
            return this.alloc_site.equals(other.alloc_site);
        }
        return false;
    }
}
