
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

    Optimization(CallGraph cg)
    {
        this.cg = cg;
    }

    public record inlineTarget(SootMethod inlinee, Stmt site, SootMethod container){}

    /**
     * This method does basic inlining, which is see the Soot-generated Call 
     * Graph, and inline the callsites with a unique target
     */
    public void basicInlining()
    {
        List<inlineTarget> tgts = new ArrayList<>();
        for(SootClass sc : Scene.v().getApplicationClasses())
        {
            for(SootMethod sm : sc.getMethods())
            {
                Body body = sm.getActiveBody();
                for(Unit u : body.getUnits())
                {
                    Stmt stmt = (Stmt) u;
                    if(stmt.containsInvokeExpr()) 
                    {
                        Iterator<Edge> e = cg.edgesOutOf(u);
                        if(e.hasNext())
                        {
                            SootMethod callee = e.next().tgt();
                            if(!e.hasNext())
                            {
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

        for(var ir : tgts)
        {
            // System.out.println("here2");
            SiteInliner.inlineSite(ir.inlinee, ir.site, ir.container);
        }  
    }


    /**
     * This function will use the points to analysis to potentially find more 
     * callsites with unique target, and inline those
     */
    public void betterInlining(SootClass sc, SootMethod sm)
    {
        if (!sm.hasActiveBody())    return;
        Body body = sm.getActiveBody();

        for(Unit u : body.getUnits())
        {
            Stmt stmt = (Stmt) u;
            
        }


    }


}
