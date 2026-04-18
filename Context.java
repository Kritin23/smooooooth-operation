
import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

public class Context {
    SootMethod sm;
    HeapObj recv;

    Context(SootMethod sm, HeapObj recv) {
        this.sm = sm;
        this.recv = recv;
    }

    static Set<Context> buildContexts(InvokeExpr ie, PTG inPtg)
    {
        Set<Context> ctxs = new HashSet<>();
        SootMethod sm = ie.getMethod();
        if(ie instanceof InstanceInvokeExpr)
        {
            InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
            Set<HeapObj> objs = inPtg.getStack((Local)iie.getBase());
            for(var o : objs)
                ctxs.add(new Context(sm, o));
        }
        else 
        {
            ctxs.add(new Context(sm, null));
        }

        return ctxs;
    }

    @Override
    public String toString() {
        String recvStr = (recv == null)
                ? "null"
                : "Obj_" + recv.alloc_site.getJavaSourceStartLineNumber();

        return String.format(
                "%s.%s::%s()",
                recvStr,
                sm.getDeclaringClass(), sm.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(sm, recv);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MethodContext mc))
            return false;
        return Objects.equals(sm, mc.sm) &&
                Objects.equals(recv, mc.recv);
    }
}
