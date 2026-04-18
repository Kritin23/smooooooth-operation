
/* Optimization.java */
import java.util.*;

import fj.Hash;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.invoke.SiteInliner;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

public class Optimization {
    CallGraph cg;

    Optimization(CallGraph cg) {
        this.cg = cg;
    }

    public record inlineTarget(SootMethod inlinee, Stmt site, SootMethod container) {
    }

    /**
     * This method does basic inlining, which is see the Soot-generated Call
     * Graph, and inline the callsites with a unique target
     */
    public void basicInlining() {
        System.out.println("Basic Inlining Targets: ");
        List<inlineTarget> tgts = new ArrayList<>();
        
        for (SootClass sc : Scene.v().getApplicationClasses()) 
        {
            for (SootMethod sm : sc.getMethods()) 
            {
                if (!sm.hasActiveBody())    continue;
                Body body = sm.getActiveBody();
                for (Unit u : body.getUnits()) 
                {
                    Stmt stmt = (Stmt) u;
                    if (stmt.containsInvokeExpr()) 
                    {
                        InvokeExpr ie = stmt.getInvokeExpr();
                        if (ie.getMethod().isConstructor()) 
                        {
                            SootMethod ctor = ie.getMethod();

                            // tgts.add(new inlineTarget(ctor, stmt, sm));
                            continue;
                        }
                        else 
                        {

                            Iterator<Edge> e = cg.edgesOutOf(u);
                            Set<SootMethod> targets = new HashSet<>();
                            while (e.hasNext())
                                targets.add(e.next().tgt());
                            System.out.println(stmt);
                            for (var s : targets)
                                System.out.println("\t" + s);

                            e = cg.edgesOutOf(u);
                            if (e.hasNext()) {
                                SootMethod callee = e.next().tgt();
                                if (!e.hasNext()) {
                                    // is singleton
                                    // SiteInliner.inlineSite(callee, null, sm)
                                    // System.out.println("here");
                                    tgts.add(new inlineTarget(callee, stmt, sm));
                                }
                            }
                        }
                    }
                }
            }
        }

        for (var ir : tgts) {
            if (ir.inlinee == ir.container)
                continue;
            System.out.println("Basic inlining: " + ir);
            // System.out.println("here2");
            SiteInliner.inlineSite(ir.inlinee, ir.site, ir.container);
        }

        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                if (!sm.hasActiveBody())
                    continue;
                Body body = sm.getActiveBody();
                body.validate();
            }
        }
    }

    /**
     * This function will use the points to analysis to potentially find more
     * callsites with unique target, and inline those
     */
    public void betterInlining(SootClass sc, SootMethod sm) {
        List<inlineTarget> inlineSites = new ArrayList<>();
        if (!sm.hasActiveBody())
            return;
        Body body = sm.getActiveBody();

        for (Unit u : body.getUnits()) {
            Stmt stmt = (Stmt) u;
            if (!stmt.containsInvokeExpr())
                continue;
            MethodAnalysis.CallSite cs = new MethodAnalysis.CallSite(sm, stmt);
            Set<SootMethod> tgts = MethodAnalysis.targets.getOrDefault(cs, null);
            if (tgts == null)
                continue;

            if (tgts.size() == 1) {
                SootMethod inlinee = tgts.iterator().next();
                inlineSites.add(new inlineTarget(inlinee, stmt, sm));
            }
        }

        for (var ir : inlineSites) {
            if (ir.inlinee == ir.container)
                continue;
            if (ir.inlinee.isConstructor())
                continue;
            // System.out.println("here2");
            System.out.println("Better inlining: " + ir);
            SiteInliner.inlineSite(ir.inlinee, ir.site, ir.container);
        }

        body.validate();

    }

    public void setCG(CallGraph _cg) {
        this.cg = _cg;
    }
}
