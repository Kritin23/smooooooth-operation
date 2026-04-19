
/* Optimization.java */
import java.util.*;

import fj.Hash;
import polyglot.ast.Assign;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.invoke.SiteInliner;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;
import soot.toolkits.graph.ExceptionalUnitGraph;

public class Optimization {
    CallGraph cg;
    Set<Unit> inlined = new HashSet<>();

    Optimization(CallGraph cg) {
        this.cg = cg;
    }


    public record InlineTarget(SootMethod inlinee, Stmt site, SootMethod container) {
    }
    /**
     * This method does basic inlining, which is see the Soot-generated Call
     * Graph, and inline the callsites with a unique target
     */
    public void basicInlining() {
        System.out.println("Basic Inlining Targets: ");
        List<InlineTarget> tgts = new ArrayList<>();
        
        for (SootClass sc : Scene.v().getApplicationClasses()) 
        {
            for (SootMethod sm : sc.getMethods()) 
            {
                if(sm.isConstructor())  continue;
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
                                    tgts.add(new InlineTarget(callee, stmt, sm));
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
            if (inlined.contains(ir.site))   
                continue;
            if(ir.inlinee.isConstructor())  continue;
            if(!ir.inlinee.hasActiveBody()) continue;
            System.out.println("Basic inlining: " + ir);
            // System.out.println("here2");
            inlined.add(ir.site);
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
        List<Stmt> splitTargets = new ArrayList<>();
        List<InlineTarget> inlineSites = new ArrayList<>();
        if (!sm.hasActiveBody())
            return;
        Body body = sm.getActiveBody();
        Chain<Unit> units = body.getUnits();

        for (Unit u : body.getUnits()) {
            Stmt stmt = (Stmt) u;
            if (!stmt.containsInvokeExpr())
                continue;
            MethodAnalysis.CallSite cs = new MethodAnalysis.CallSite(sm, stmt);
            Set<SootMethod> tgts = MethodAnalysis.targets.getOrDefault(cs, null);
            if (tgts == null)
                continue;
            System.out.println("debug targets:");
            System.out.println(tgts);
            if (tgts.size() == 1) {
                SootMethod inlinee = tgts.iterator().next();
                inlineSites.add(new InlineTarget(inlinee, stmt, sm));
            }
            else 
            {
                System.out.println("Adding to splitting");
                splitTargets.add(stmt);
            }
        }
        
        
        for(var stmt : splitTargets)
        {
            MethodAnalysis.CallSite cs = new MethodAnalysis.CallSite(sm, stmt);
            Splitting(sm,stmt, MethodAnalysis.targets.getOrDefault(cs, null));
        }
        for (var ir : inlineSites) {
            if (ir.inlinee == ir.container)
                continue;
            if (ir.inlinee.isConstructor())
                continue;
            if (inlined.contains(ir.site))   
                continue;
            // System.out.println("here2");
            System.out.println("Better inlining: " + ir);

            
            Stmt stmt = ir.site;
            InvokeExpr ie = stmt.getInvokeExpr();

            if(ie instanceof InstanceInvokeExpr)
            {
                InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
                Local recv_local = (Local) iie.getBase();
                SootClass className = ir.inlinee.getDeclaringClass();
                // Need to insert a cast before inlining
                Local temp = Jimple.v().newLocal("temp", className.getType());
                body.getLocals().add(temp);
                CastExpr ce = Jimple.v().newCastExpr(recv_local, className.getType());
                Unit castAssign = Jimple.v().newAssignStmt(temp, ce);
                
                units.insertBefore(castAssign, stmt);
                iie.setBase(temp);
                
            }

            inlined.add(ir.site);
            SiteInliner.inlineSite(ir.inlinee, ir.site, ir.container);
        }

        body.validate();

    }

    
    public void Splitting(SootMethod sm, Stmt stmt, Set<SootMethod> targets)
    {
        System.out.println("Splitting called");
        if(targets == null) return;
        if(!AnalysisTransformer.doSplitting)    return;
        if(targets.size() > 3) return;
        System.out.println("Splitting: " + stmt);

        if(!(stmt instanceof AssignStmt || stmt instanceof InvokeStmt)) return;
        
        InvokeExpr ie = stmt.getInvokeExpr();
        if(!(ie instanceof InstanceInvokeExpr)) return;
        if(stmt instanceof AssignStmt)
        {
            Value st = ((AssignStmt) stmt).getRightOp();
            if(!(st instanceof InstanceInvokeExpr)) return;
        }

        List<InlineTarget> inlineTgts = new ArrayList<>();
        
        InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
        
        Local base = (Local) iie.getBase();
        Body body = sm.getActiveBody();
        Chain<Unit> units = body.getUnits();

        Unit end_lbl = Jimple.v().newNopStmt();
        units.insertAfter( end_lbl, stmt);
        Unit goto_end_stmt = Jimple.v().newGotoStmt(end_lbl);

        List<Unit> iocond = new ArrayList<>();
        List<Unit> branch = new ArrayList<>();
        List<Unit> cast = new ArrayList<>();
        List<Unit> invoke = new ArrayList<>();
        List<Unit> gotoEnd = new ArrayList<>();

        for(SootMethod tgt : targets)
        {
            List<Value> args = ie.getArgs();
            SootClass cl = tgt.getDeclaringClass();
            Type tp = cl.getType();

            Value iocheck = Jimple.v().newInstanceOfExpr(base, tp);
            Local cond_local = Jimple.v().newLocal("cond"+cl.getName(), 
                                                    BooleanType.v());
            body.getLocals().add(cond_local);
            Unit ioStmt = Jimple.v().newAssignStmt(cond_local, iocheck);

            Local cast_local = Jimple.v().newLocal("tmp"+cl.getName(), tp);
            body.getLocals().add(cast_local);
            Unit cast_stmt = Jimple.v().newAssignStmt(
                cast_local, 
                Jimple.v().newCastExpr(base, tp)
            );
            InvokeExpr invoke_expr = Jimple.v().newVirtualInvokeExpr(
                cast_local, 
                tgt.makeRef(),
                args
            );

            Unitr castInvoke;
            if(stmt instanceof InstanceInvokeExpr)
            {
                // castInvoke = (Unit) invoke_expr;
                castInvoke = Jimple.v().newInvokeStmt(invoke_expr);
            }
            else if(stmt instanceof AssignStmt) 
            {
                castInvoke = Jimple.v().newAssignStmt(
                    ((AssignStmt) stmt).getLeftOp(),
                    invoke_expr
                );
            }
            else 
            {
                return;
            }

            Unit branchStmt = Jimple.v().newIfStmt(
                Jimple.v().newNeExpr(cond_local, IntConstant.v(0)),
                cast_stmt);

            Unit gotoEndStmt = Jimple.v().newGotoStmt(end_lbl);

            iocond.add(ioStmt);
            branch.add(branchStmt);
            cast.add(cast_stmt);
            invoke.add(castInvoke);
            gotoEnd.add(gotoEndStmt);
        }

        for(int i=0;i<targets.size();i++)
        {
            units.insertBefore(iocond.get(i), stmt);
            units.insertBefore( branch.get(i), stmt);
        }
        for(int i=0;i<targets.size();i++)
        {
            units.insertAfter( gotoEnd.get(i), stmt);
            units.insertAfter( invoke.get(i), stmt);
            units.insertAfter( cast.get(i), stmt);

            SootMethod tgt = ((Stmt) invoke.get(i)).getInvokeExpr().getMethod();

            inlineTgts.add(new InlineTarget(tgt, (Stmt)invoke.get(i), sm));

        }

        units.insertAfter( goto_end_stmt, stmt);

        body.validate();

        for(var ir : inlineTgts)
        {
            if (ir.inlinee == ir.container)
                continue;
            if (inlined.contains(ir.site))   
                continue;
            if(ir.inlinee.isConstructor())  continue;
            if(!ir.inlinee.hasActiveBody()) continue;
            System.out.println("Split inlining: " + ir);
            // System.out.println("here2");
            inlined.add(ir.site);
            SiteInliner.inlineSite(ir.inlinee, ir.site, ir.container);
        }


        body.validate();
    }


    public void setCG(CallGraph _cg) {
        this.cg = _cg;
    }
}
