/* MethodAnalysis.java */
import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;


public class MethodAnalysis {
    record CallSite(SootMethod method, Unit unit) {}
    record AnalysisInfo(Set<HeapObj> ret, PTG outPtg) {
        AnalysisInfo() {
            this(new HashSet<>(), new PTG());
        }
    }
    record Result (
        Map<Unit, Set<SootMethod>> targets,
        Map<Unit, Set<HeapObj>> receivers
    ) {
        Result(){
            this(new HashMap<>(), new HashMap<>());
        }
    }

    // This stores the results of all analysis
    static HashMap<Context, Result> allResults = new HashMap<>();
    static HashMap<CallSite, Set<SootMethod>> targets = new HashMap<>();
    // static HashMap<Context, PTG> outPTGs = new HashMap<>();


    SootMethod method;
    Body body;
    CallGraph cg;
    HeapObj recv;
    // List<Local> params;
    List<Set<HeapObj>> params;
    PTG inPtg;
    Context ctx;
    Result analysisRes;
    boolean analysisNeeded;
    ForwardDFA parent;
    Set<HeapObj> returned;
    boolean emptyReturn = false;

    Map<Unit, PTG> ptgBefore = new HashMap<>();
    Map<Unit, PTG> ptgAfter = new HashMap<>();

    // Map<HeapObj, Boolean> scalarReplaceable = new HashMap<>();
    // Map<HeapObj, Set<Integer>> objCallSites = new HashMap<>();
    // static HashMap<Context, PTG> methodInPTG = new HashMap<>();
    // static HashSet<Context> onStack = new HashSet<>();

    MethodAnalysis(SootMethod sm,
            CallGraph _cg,
            HeapObj recv,
            List<Set<HeapObj>> params,
            PTG inPtg,
            ForwardDFA dfa) {
        
        this.method = sm;
        if(sm.hasActiveBody())
            this.body = sm.getActiveBody();
        this.cg = _cg;
        this.recv = recv;
        this.params = params;
        this.inPtg = inPtg.copy();
        this.analysisNeeded = true;
        this.analysisRes = new Result();
        this.parent = dfa;
        this.returned = new HashSet<>();

        // build a context here
        this.ctx = new Context(sm, recv);   



        // List<Type> paramTypes = new ArrayList<>();
        // for (var p : params) {
        //     Set<HeapObj> objs = inPtg.getStack(p);
        //     Type tp = null;
        //     boolean uninit = true;
        //     for (HeapObj o : objs) {
        //         if (uninit) {
        //             tp = o.getObjectType();
        //             uninit = false;
        //         } else if (tp != o.getObjectType()) {
        //             tp = null;
        //         }
        //     }

        //     paramTypes.add(tp);

        // }



        
        // if(onStack.contains(ctx))
        //     analysisNeeded = false;
        // if (MethodAnalysis.methodInPTG.containsKey(this.ctx)) {
        //     PTG newPTG = this.inPtg.copy();
        //     newPTG.merge(MethodAnalysis.methodInPTG.get(this.ctx));
        //     if (this.inPtg.equals(newPTG)) {
        //         analysisNeeded = false;
        //     }
        //     this.inPtg = newPTG;
        //     MethodAnalysis.methodInPTG.replace(this.ctx, newPTG);
        // } else {
        //     MethodAnalysis.methodInPTG.put(this.ctx, this.inPtg);
        // }

        // System.out.println("Analysing " + this.ctx);

    }

    PTG transfer(Stmt stmt, PTG in) {

        PTG ptg = in.copy();
        // System.out.println(stmt);
        if (stmt.containsInvokeExpr()) {
            // System.out.println("here");
            ptg = handleCall(stmt, in);

            // if x = foo(...)
            // if (stmt instanceof AssignStmt) {
            //     AssignStmt assign = (AssignStmt) stmt;
            //     Value lhs = assign.getLeftOp();

                // if (lhs instanceof Local) {
                //     // conservative: unknown return value
                //     ptg.stack.remove((Local) lhs);
                // }
            // }
        }

        if (stmt instanceof AssignStmt) {
            AssignStmt assign = (AssignStmt) stmt;
            Value lhs = assign.getLeftOp();
            Value rhs = assign.getRightOp();

            // x = new A();
            if (lhs instanceof Local && rhs instanceof NewExpr) {
                HeapObj obj = new HeapObj(stmt, this.ctx);
                // scalarReplaceable.put(obj, true); // initially assume replaceable
                ptg.stack.put((Local) lhs, new HashSet<>(Set.of(obj)));
            } 
            // x.f = new A
            else if (lhs instanceof InstanceFieldRef && rhs instanceof NewExpr) {
                InstanceFieldRef fr = (InstanceFieldRef) lhs;
                if (!(fr.getBase() instanceof Local))
                    return ptg;

                Local base = (Local) fr.getBase();
                HeapObj obj = new HeapObj(stmt, this.ctx);
                // scalarReplaceable.put(obj, true); // initially assume replaceable

                for (HeapObj baseObj : ptg.getStack(base)) {
                    ptg.addHeap(baseObj, fr.getField(), obj);
                }
            }

            // x = null
            else if (lhs instanceof Local &&
                    !(rhs instanceof Local) &&
                    !(rhs instanceof InstanceFieldRef) &&
                    !(rhs instanceof StaticFieldRef) &&
                    !(rhs instanceof InvokeExpr) && 
                    !(rhs instanceof CastExpr)) {

                // constants kill points-to
                ptg.stack.remove((Local) lhs);
            }

            // x = (Cast) y
            else if (lhs instanceof Local && rhs instanceof CastExpr) {
                Local loc =(Local) ((CastExpr) rhs).getOp();
                ptg.stack.put((Local) lhs,
                        new HashSet<>(ptg.getStack(loc)));
            }

            // x = y
            else if (lhs instanceof Local && rhs instanceof Local) {
                ptg.stack.put((Local) lhs,
                        new HashSet<>(ptg.getStack((Local) rhs)));
            }

            // x.f = y;
            else if (lhs instanceof InstanceFieldRef) {

                InstanceFieldRef fr = (InstanceFieldRef) lhs;

                if (!(fr.getBase() instanceof Local))
                    return ptg;

                Local base = (Local) fr.getBase();

                Set<HeapObj> rhsPts = new HashSet<>();
                if (rhs instanceof Local) {
                    rhsPts = ptg.getStack((Local) rhs);
                }

                for (HeapObj x_obj : ptg.getStack(base)) {
                    for (HeapObj y_obj : rhsPts) {
                        ptg.addHeap(x_obj, fr.getField(), y_obj);
                    }

                    // if x escapes, so does y
                    // if (!scalarReplaceable.getOrDefault(x_obj, true)) {
                    // for (HeapObj o : rhsPts) {
                    // scalarReplaceable.put(o, false);
                    // }
                    // }
                }
            }

            // x = y.f;
            else if (lhs instanceof Local && rhs instanceof InstanceFieldRef) {

                InstanceFieldRef fr = (InstanceFieldRef) rhs;
                if (!(fr.getBase() instanceof Local))
                    return ptg;

                Local base = (Local) fr.getBase();
                Set<HeapObj> result = new HashSet<>();

                for (HeapObj obj : ptg.getStack(base)) {
                    Set<HeapObj> ho = ptg.getHeap(obj, fr.getField());
                    if(ho.isEmpty())
                    {
                        result.clear();
                        break;
                    }
                    else
                        result.addAll(ho);
                }

                ptg.stack.put((Local) lhs, result);
            }
            // x.f = y where x is static/global, then all objects pointed by y
            // is not scalar replaceable

            // else if (lhs instanceof StaticFieldRef && rhs instanceof Local) {
            // if (rhs instanceof Local) {
            // // for (HeapObj obj : ptg.reachable((Local) rhs)) {
            // // scalarReplaceable.put(obj, false);
            // // }
            // }
            // }

            // x = y where y is static/global, then x can point anywhere
            else if (lhs instanceof Local && rhs instanceof StaticFieldRef) {
                // conservative: unknown global
                ptg.stack.remove((Local) lhs);
            }
        }

        // Handle Params copying
        if (stmt instanceof IdentityStmt) {
            IdentityStmt iden = (IdentityStmt) stmt;
            Local lhs = (Local) iden.getLeftOp();
            Value rhs_val = iden.getRightOp();
            Set<HeapObj> pm;
            if (rhs_val instanceof ParameterRef) {
                ParameterRef rhs = (ParameterRef) rhs_val;
                pm = params.get(rhs.getIndex());

                PTG copied = inPtg.copy_restrict(pm);
                ptg.merge(copied);
                for (var ho : pm) {
                    ptg.addStack(lhs, ho);
                }
            } else if (rhs_val instanceof ThisRef) {
                Set<HeapObj> objs = new HashSet<>();
                objs.add(recv);
                PTG copied = inPtg.copy_restrict(objs);
                ptg.merge(copied);
                ptg.addStack(lhs, recv);
            } else {
                // throw new NullPointerException("unhandled identity stmt");
            }

        }
        
        if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (!(ret.getOp() instanceof Local))
            {
                emptyReturn = true;
                returned.clear();
            }
            else 
            {
                Local loc = (Local) ret.getOp();
                Set<HeapObj> objs = ptg.getStack(loc);
                returned.addAll(objs);
            }
            // ptg.addS
        }
        return ptg;
    }

    PTG handleCall(Stmt stmt, PTG ptg) {
        InvokeExpr invoke = stmt.getInvokeExpr();

        // System.out.println("CallStmt: " + invoke);
        // System.out.println("PTG: ");
        // System.out.println(ptg);
        // System.out.println("*********************");

        PTG callOutPTG = ptg.copy();

        SootMethod declMethod = invoke.getMethod();
        // int lineNo = stmt.getJavaSourceStartLineNumber();
        Set<HeapObj> objs;
        if (invoke instanceof InstanceInvokeExpr) {
            InstanceInvokeExpr iinvoke = (InstanceInvokeExpr) invoke;
            objs = ptg.getStack((Local) iinvoke.getBase());
        } else {
            objs = null;
        }


        if (objs == null || objs.isEmpty()) {
            Iterator<Edge> targets = cg.edgesOutOf(stmt);

            while (targets.hasNext()) {
                SootMethod called = targets.next().tgt();

                analysisRes.targets.
                    computeIfAbsent(stmt, k -> new HashSet<>()).add(called);

                if (!called.hasActiveBody())
                    continue;

                List<Value> param_val = invoke.getArgs();
                List<Local> param_loc = new ArrayList<>();
                List<Set<HeapObj>> param_ho = new ArrayList<>();

                for (int i = 0; i < param_val.size(); i++) {
                    if (param_val.get(i) instanceof Local)
                        param_loc.add((Local) param_val.get(i));
                    else
                        param_loc.add(null);
                }
                for(var l : param_loc)
                {
                    param_ho.add(ptg.getStack(l));
                }
                // MethodAnalysis ma = new MethodAnalysis(
                //         called, cg, null, param_loc, ptg);

                // PTG out = ma.runAnalysis();
                AnalysisInfo info = parent.queueMethod(called, 
                    null, param_ho, ptg, ctx);

                callOutPTG.merge(info.outPtg);
                if(stmt instanceof AssignStmt)
                {
                    AssignStmt as = (AssignStmt) stmt;
                    Local lhs = (Local) as.getLeftOp();
                    // Local lhs = (Local) ;
                    for(var o : info.ret)
                    {
                        callOutPTG.addStack(lhs, o);
                    }
                }

            }
        } else {
            for (var o : objs) {
                RefType tp = (RefType) o.getObjectType();
                SootClass sc = tp.getSootClass();
                SootMethod called = declMethod;
                if(!declMethod.isConstructor())
                    called = Scene.v().getActiveHierarchy()
                        .resolveConcreteDispatch(sc, declMethod);

                if (called == null)
                    continue;

                CallSite cs = new CallSite(method, stmt);
                analysisRes.targets.
                    computeIfAbsent(stmt, k -> new HashSet<>()).add(called);
                targets.computeIfAbsent(cs, k->new HashSet<>()).
                    add(called);

                List<Value> param_val = invoke.getArgs();
                List<Local> param_loc = new ArrayList<>();
                List<Set<HeapObj>> param_ho = new ArrayList<>();

                for (int i = 0; i < param_val.size(); i++) {
                    if (param_val.get(i) instanceof Local)
                        param_loc.add((Local) param_val.get(i));
                    else
                        param_loc.add(null);
                }
                for(var l : param_loc)
                {
                    param_ho.add(ptg.getStack(l));
                }

                // System.out.println("Calling: " + stmt);
                AnalysisInfo info = parent.queueMethod(called, 
                    o, param_ho, ptg, ctx);

                
                Set<Local> locals = new HashSet<>(info.outPtg.stack.keySet());
                for(var l : locals)
                    info.outPtg.stack.remove(l);

                callOutPTG.merge(info.outPtg);

                if(stmt instanceof AssignStmt)
                {
                    AssignStmt as = (AssignStmt) stmt;
                    Local lhs = (Local) as.getLeftOp();
                    // Local lhs = (Local) ;
                    for(var reto : info.ret)
                    {
                        callOutPTG.addStack(lhs, reto);
                    }
                }
            }
        }

        analysisRes.receivers.put(stmt, objs);

        // System.out.println("CAll-Out PTG: ");
        // System.out.println(callOutPTG);

        return callOutPTG;

        // Iterator<Edge> targets = cg.edgesOutOf(stmt);
        // while (targets.hasNext()) {
        // Edge edge = targets.next();
        // SootMethod target_method = edge.tgt();

        // // if (!target_method.getDeclaringClass().isApplicationClass())
        // // continue;
        // if (!target_method.hasActiveBody())
        // continue;

        // // boolean usefulTgt = false;

        // // Build a context and call MethodAnalysis

        // }
    }

    boolean isExit(Unit u) {
        return (u instanceof ReturnStmt)
                || (u instanceof ReturnVoidStmt)
                || (u instanceof ThrowStmt);
    }

    /**
     * Run analysis on this method, and store result in a global
     * table. Given an inPtg, compute an outPtg
     * 
     */
    AnalysisInfo runAnalysis() {

        if(!method.hasActiveBody())
            return new AnalysisInfo();

        // if (!analysisNeeded)
        // {
        //     System.out.println("Returning from Map");
        //     System.out.println(outPTGs.get(ctx));
        //     return outPTGs.computeIfAbsent(ctx, (k) -> new PTG());
        // }
        
        // System.out.println("STARTING: " + ctx);
        // System.out.println(this.ctx);
        // System.out.println("Params: " + params);
        // System.out.println("PTG: ");
        // System.out.println(inPtg);

        // onStack.add(ctx);
        UnitGraph cfg = new ExceptionalUnitGraph(body);
        PTG globalOutPtg = new PTG();

        // initialize
        for (Unit u : body.getUnits()) {
            ptgBefore.put(u, new PTG());
            ptgAfter.put(u, new PTG());
        }

        boolean changed = true;

        while (changed) {
            changed = false;

            for (Unit u : body.getUnits()) {
                Stmt stmt = (Stmt) u;

                // if a.x is written to and a points to more than one object, then all those
                // objects are not scalar replaceable
                if (stmt instanceof AssignStmt) {
                    AssignStmt assign = (AssignStmt) stmt;
                    Value lhs = assign.getLeftOp();

                    if (lhs instanceof InstanceFieldRef) {
                        InstanceFieldRef fr = (InstanceFieldRef) lhs;
                        Local base = (Local) fr.getBase();
                        Set<HeapObj> baseObjs = ptgBefore.get(u).getStack(base);
                        if (baseObjs.size() > 1) {
                            for (HeapObj obj : baseObjs) {
                                // scalarReplaceable.put(obj, false);
                            }
                        }
                    }
                }

                PTG in = new PTG();

                for (Unit pred : cfg.getPredsOf(u)) {
                    in.merge(ptgAfter.get(pred));
                }

                PTG oldOut = ptgAfter.get(u).copy();
                PTG newOut = transfer(stmt, in);

                if (!newOut.equals(oldOut)) {
                    ptgAfter.put(u, newOut);
                    ptgBefore.put(u, in);
                    changed = true;
                }
            }
        }

        allResults.put(ctx, this.analysisRes);
        // onStack.remove(ctx);

        for(var u : body.getUnits())
        {
            if(isExit(u))
            {
                globalOutPtg.merge(ptgAfter.get(u));
            }
        }

        // outPTGs.put(ctx, globalOutPtg);

        // System.out.println("Context: " + ctx);
        // System.out.println("OUT PTG:");
        // System.out.println(globalOutPtg);

        return new AnalysisInfo(!emptyReturn ? returned : new HashSet<>(), globalOutPtg);

        // propagateNonScalarReplaceable(globalMergedPTG);
        // return scalarReplaceable;
    }

    static void clearAllResults() {
        allResults = new HashMap<>();
        targets = new HashMap<>();
    }

    static void printTargets()
    {
        for(var cs : targets.keySet())
        {
            System.out.println(cs + ": ");
            for(var sm : targets.get(cs))
            {
                System.out.println("\t" + sm);
            }
        }
    }
}

/*
 * // check if method writes which takes
 * void checkWrites(SootMethod callee, PTG caller_ptg, InvokeExpr invoke) {
 * 
 * if (!callee.getDeclaringClass().isApplicationClass())
 * return;
 * if (!callee.hasActiveBody())
 * return;
 * 
 * Set<HeapObj> reachable_objs = new HashSet<>();
 * 
 * Body callee_body = callee.getActiveBody();
 * PTG callee_ptg = new PTG();
 * 
 * // if method call is a.foo(), callee ptg should know 'this' points to 'a'
 * if (invoke instanceof InstanceInvokeExpr) {
 * Local receiver = (Local) ((InstanceInvokeExpr) invoke).getBase();
 * Local thisLocal = callee_body.getThisLocal();
 * callee_ptg.stack.put(thisLocal,
 * new HashSet<>(caller_ptg.getStack(receiver)));
 * for (HeapObj obj : caller_ptg.reachable(receiver)) {
 * reachable_objs.add(obj);
 * if (caller_ptg.heap.containsKey(obj)) {
 * for (Map.Entry<SootField, Set<HeapObj>> e :
 * caller_ptg.heap.get(obj).entrySet()) {
 * for (HeapObj target : e.getValue()) {
 * callee_ptg.addHeap(obj, e.getKey(), target);
 * }
 * }
 * }
 * }
 * }
 * 
 * // map args to formal params
 * List<Value> args = invoke.getArgs();
 * List<Local> params = callee_body.getParameterLocals();
 * for (int i = 0; i < args.size(); i++) {
 * if (args.get(i) instanceof Local) {
 * Local arg = (Local) args.get(i);
 * 
 * // for all reachable objs, copy ptg info to callee ptg
 * for (HeapObj obj : caller_ptg.reachable(arg)) {
 * // add methodLines entry
 * callee_ptg.stack.put(params.get(i), new HashSet<>(caller_ptg.getStack(arg)));
 * // copy heap edges of reachable objects
 * if (caller_ptg.heap.containsKey(obj)) {
 * for (Map.Entry<SootField, Set<HeapObj>> e :
 * caller_ptg.heap.get(obj).entrySet()) {
 * for (HeapObj target : e.getValue()) {
 * callee_ptg.addHeap(obj, e.getKey(), target);
 * }
 * }
 * }
 * }
 * }
 * }
 * 
 * for (Value arg : args) {
 * if (arg instanceof Local) {
 * reachable_objs.addAll(caller_ptg.reachable((Local) arg));
 * }
 * }
 * 
 * for (Unit u : callee_body.getUnits()) {
 * Stmt stmt = (Stmt) u;
 * if (stmt instanceof AssignStmt) {
 * AssignStmt assign = (AssignStmt) stmt;
 * Value lhs = assign.getLeftOp();
 * 
 * // if x.f = y, then all objects that x points to are not
 * // scalar replaceable
 * // System.out.println("Statement: " + stmt + " line number " +
 * // stmt.getJavaSourceStartLineNumber());
 * if (lhs instanceof InstanceFieldRef) {
 * InstanceFieldRef fr = (InstanceFieldRef) lhs;
 * Local base = (Local) fr.getBase();
 * for (HeapObj obj : callee_ptg.getStack(base)) {
 * // System.out.println("checking alloc site: " + obj.alloc_site +
 * " for method: "
 * // + callee.getName());
 * if (reachable_objs.contains(obj)) {
 * scalarReplaceable.put(obj, false);
 * }
 * }
 * }
 * }
 * 
 * else if (stmt.containsInvokeExpr()) {
 * InvokeExpr inner_invoke = (InvokeExpr) stmt.getInvokeExpr();
 * Iterator<Edge> innerTargets = cg.edgesOutOf(stmt);
 * while (innerTargets.hasNext()) {
 * Edge edge = innerTargets.next();
 * SootMethod target_method = edge.tgt();
 * checkWrites(target_method, callee_ptg, inner_invoke);
 * }
 * }
 * transfer(stmt, callee_ptg);
 * }
 * }
 * 
 * void propagateNonScalarReplaceable(PTG globalMergedPTG) {
 * boolean propagationChanged = true;
 * while (propagationChanged) {
 * propagationChanged = false;
 * 
 * for (Map.Entry<HeapObj, Boolean> entry : scalarReplaceable.entrySet()) {
 * if (!entry.getValue()) { // this object is non-SR
 * HeapObj nonSR = entry.getKey();
 * 
 * // find all objects reachable from nonSR in globalMergedPTG
 * if (globalMergedPTG.heap.containsKey(nonSR)) {
 * for (Set<HeapObj> targets : globalMergedPTG.heap.get(nonSR).values()) {
 * for (HeapObj target : targets) {
 * if (scalarReplaceable.containsKey(target)
 * && scalarReplaceable.get(target)) {
 * scalarReplaceable.put(target, false);
 * propagationChanged = true;
 * }
 * }
 * }
 * }
 * }
 * }
 * }
 * }
 */
