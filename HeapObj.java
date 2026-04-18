import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;



public class HeapObj {
    Unit alloc_site;
    Context ctx;

    HeapObj(Unit alloc_site, Context ctx) {
        this.alloc_site = alloc_site;
        this.ctx = ctx;
    }

    public String toString() {
        return String.format(
            "Obj_%s (%s) @ %s", 
            alloc_site.getJavaSourceStartLineNumber(),
            getObjectType(),
            ctx.toString()
            );
    }

    Type getStaticType() {
        JAssignStmt stmt = (JAssignStmt) alloc_site;
        Value lhs = stmt.getLeftOp();
        return lhs.getType();
    }   

    Type getObjectType() {
        JAssignStmt stmt = (JAssignStmt) alloc_site;
        JNewExpr new_e = (JNewExpr) stmt.getRightOp();
        return new_e.getType();
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
