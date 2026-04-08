
import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

public class MethodAnalysis {

    SootMethod method;
    Body body;
    CallGraph cg;

    Map<Unit, PTG> ptgBefore = new HashMap<>();
    Map<Unit, PTG> ptgAfter = new HashMap<>();

    Map<HeapObj, Boolean> scalarReplaceable = new HashMap<>();
    Map<HeapObj, Set<Integer>> objCallSites = new HashMap<>();

    PTG transfer(Stmt stmt, PTG in) {

        PTG ptg = in.copy();

        if (stmt.containsInvokeExpr()) {
            handleCall(stmt, ptg);

            // if x = foo(...)
            if (stmt instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) stmt;
                Value lhs = assign.getLeftOp();

                if (lhs instanceof Local) {
                    // conservative: unknown return value
                    ptg.stack.remove((Local) lhs);
                }
            }
        }

        if (stmt instanceof AssignStmt) {
            AssignStmt assign = (AssignStmt) stmt;
            Value lhs = assign.getLeftOp();
            Value rhs = assign.getRightOp();

            // x = new A();
            if (lhs instanceof Local && rhs instanceof NewExpr) {
                HeapObj obj = new HeapObj(stmt);
                scalarReplaceable.put(obj, true); // initially assume replaceable
                ptg.stack.put((Local) lhs, new HashSet<>(Set.of(obj)));
            } else if (lhs instanceof InstanceFieldRef && rhs instanceof NewExpr) {
                InstanceFieldRef fr = (InstanceFieldRef) lhs;
                if (!(fr.getBase() instanceof Local))
                    return ptg;

                Local base = (Local) fr.getBase();
                HeapObj obj = new HeapObj(stmt);
                scalarReplaceable.put(obj, true); // initially assume replaceable

                for (HeapObj baseObj : ptg.getStack(base)) {
                    ptg.addHeap(baseObj, fr.getField(), obj);
                }
            }

            // x = null
            else if (lhs instanceof Local &&
                    !(rhs instanceof Local) &&
                    !(rhs instanceof InstanceFieldRef) &&
                    !(rhs instanceof StaticFieldRef) &&
                    !(rhs instanceof InvokeExpr)) {

                // constants kill points-to
                ptg.stack.remove((Local) lhs);
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
                    if (!scalarReplaceable.getOrDefault(x_obj, true)) {
                        for (HeapObj o : rhsPts) {
                            scalarReplaceable.put(o, false);
                        }
                    }
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
                    result.addAll(ptg.getHeap(obj, fr.getField()));
                }

                ptg.stack.put((Local) lhs, result);
            }
            // x.f = y where x is static/global, then all objects pointed by y is not scalar
            // replaceable
            else if (lhs instanceof StaticFieldRef && rhs instanceof Local) {
                if (rhs instanceof Local) {
                    for (HeapObj obj : ptg.reachable((Local) rhs)) {
                        scalarReplaceable.put(obj, false);
                    }
                }
            }

            // x = y where y is static/global, then x can point anywhere
            else if (lhs instanceof Local && rhs instanceof StaticFieldRef) {
                // conservative: unknown global
                ptg.stack.remove((Local) lhs);
            }
        }

        return ptg;
    }

    void handleCall(Stmt stmt, PTG ptg) {
        InvokeExpr invoke = stmt.getInvokeExpr();
        int lineNo = stmt.getJavaSourceStartLineNumber();

        Iterator<Edge> targets = cg.edgesOutOf(stmt);
        while (targets.hasNext()) {
            Edge edge = targets.next();
            SootMethod target_method = edge.tgt();

            if (!target_method.getDeclaringClass().isApplicationClass())
                continue;
            if (!target_method.hasActiveBody())
                continue;

            // skip constructors
            if (target_method.isConstructor())
                continue;

            // record reachability at this call site
            if (invoke instanceof InstanceInvokeExpr) {
                Local receiver = (Local) ((InstanceInvokeExpr) invoke).getBase();
                for (HeapObj obj : ptg.reachable(receiver)) {
                    objCallSites.computeIfAbsent(obj, k -> new TreeSet<>()).add(lineNo);
                }
            }
            for (Value arg : invoke.getArgs()) {
                if (arg instanceof Local) {
                    for (HeapObj obj : ptg.reachable((Local) arg)) {
                        objCallSites.computeIfAbsent(obj, k -> new TreeSet<>()).add(lineNo);
                    }
                }
            }

            checkWrites(target_method, ptg, invoke);
        }
    }

    // check if method writes which takes
    void checkWrites(SootMethod callee, PTG caller_ptg, InvokeExpr invoke) {

        if (!callee.getDeclaringClass().isApplicationClass())
            return;
        if (!callee.hasActiveBody())
            return;

        Set<HeapObj> reachable_objs = new HashSet<>();

        Body callee_body = callee.getActiveBody();
        PTG callee_ptg = new PTG();

        // if method call is a.foo(), callee ptg should know 'this' points to 'a'
        if (invoke instanceof InstanceInvokeExpr) {
            Local receiver = (Local) ((InstanceInvokeExpr) invoke).getBase();
            Local thisLocal = callee_body.getThisLocal();
            callee_ptg.stack.put(thisLocal,
                    new HashSet<>(caller_ptg.getStack(receiver)));
            for (HeapObj obj : caller_ptg.reachable(receiver)) {
                reachable_objs.add(obj);
                if (caller_ptg.heap.containsKey(obj)) {
                    for (Map.Entry<SootField, Set<HeapObj>> e : caller_ptg.heap.get(obj).entrySet()) {
                        for (HeapObj target : e.getValue()) {
                            callee_ptg.addHeap(obj, e.getKey(), target);
                        }
                    }
                }
            }
        }

        // map args to formal params
        List<Value> args = invoke.getArgs();
        List<Local> params = callee_body.getParameterLocals();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) instanceof Local) {
                Local arg = (Local) args.get(i);

                // for all reachable objs, copy ptg info to callee ptg
                for (HeapObj obj : caller_ptg.reachable(arg)) {
                    // add methodLines entry
                    callee_ptg.stack.put(params.get(i), new HashSet<>(caller_ptg.getStack(arg)));
                    // copy heap edges of reachable objects
                    if (caller_ptg.heap.containsKey(obj)) {
                        for (Map.Entry<SootField, Set<HeapObj>> e : caller_ptg.heap.get(obj).entrySet()) {
                            for (HeapObj target : e.getValue()) {
                                callee_ptg.addHeap(obj, e.getKey(), target);
                            }
                        }
                    }
                }
            }
        }

        for (Value arg : args) {
            if (arg instanceof Local) {
                reachable_objs.addAll(caller_ptg.reachable((Local) arg));
            }
        }

        for (Unit u : callee_body.getUnits()) {
            Stmt stmt = (Stmt) u;
            if (stmt instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) stmt;
                Value lhs = assign.getLeftOp();

                // if x.f = y, then all objects that x points to are not
                // scalar replaceable
                // System.out.println("Statement: " + stmt + " line number " +
                // stmt.getJavaSourceStartLineNumber());
                if (lhs instanceof InstanceFieldRef) {
                    InstanceFieldRef fr = (InstanceFieldRef) lhs;
                    Local base = (Local) fr.getBase();
                    for (HeapObj obj : callee_ptg.getStack(base)) {
                        // System.out.println("checking alloc site: " + obj.alloc_site + " for method: "
                        // + callee.getName());
                        if (reachable_objs.contains(obj)) {
                            scalarReplaceable.put(obj, false);
                        }
                    }
                }
            }

            else if (stmt.containsInvokeExpr()) {
                InvokeExpr inner_invoke = (InvokeExpr) stmt.getInvokeExpr();
                Iterator<Edge> innerTargets = cg.edgesOutOf(stmt);
                while (innerTargets.hasNext()) {
                    Edge edge = innerTargets.next();
                    SootMethod target_method = edge.tgt();
                    checkWrites(target_method, callee_ptg, inner_invoke);
                }
            }
            transfer(stmt, callee_ptg);
        }
    }

    void propagateNonScalarReplaceable(PTG globalMergedPTG) {
        boolean propagationChanged = true;
        while (propagationChanged) {
            propagationChanged = false;

            for (Map.Entry<HeapObj, Boolean> entry : scalarReplaceable.entrySet()) {
                if (!entry.getValue()) { // this object is non-SR
                    HeapObj nonSR = entry.getKey();

                    // find all objects reachable from nonSR in globalMergedPTG
                    if (globalMergedPTG.heap.containsKey(nonSR)) {
                        for (Set<HeapObj> targets : globalMergedPTG.heap.get(nonSR).values()) {
                            for (HeapObj target : targets) {
                                if (scalarReplaceable.containsKey(target)
                                        && scalarReplaceable.get(target)) {
                                    scalarReplaceable.put(target, false);
                                    propagationChanged = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Map<HeapObj, Boolean> analyzeScalarReplaceability() {

        UnitGraph cfg = new ExceptionalUnitGraph(body);
        PTG globalMergedPTG = new PTG();

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
                // if a.x is written to and a points to more than one object, then all those objects are not scalar replaceable
                if (stmt instanceof AssignStmt) {
                    AssignStmt assign = (AssignStmt) stmt;
                    Value lhs = assign.getLeftOp();

                    if (lhs instanceof InstanceFieldRef) {
                        InstanceFieldRef fr = (InstanceFieldRef) lhs;
                        Local base = (Local) fr.getBase();
                        Set<HeapObj> baseObjs = ptgBefore.get(u).getStack(base);
                        if (baseObjs.size() > 1) {
                            for (HeapObj obj : baseObjs) {
                                scalarReplaceable.put(obj, false);
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

        for (Unit u : body.getUnits()) {
            globalMergedPTG.merge(ptgAfter.get(u));
        }

        propagateNonScalarReplaceable(globalMergedPTG);
        return scalarReplaceable;
    }
}
