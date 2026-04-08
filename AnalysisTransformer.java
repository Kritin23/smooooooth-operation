import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

class HeapObj {
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

class PTG {
    Map<Local, Set<HeapObj>> stack = new HashMap<>();
    Map<HeapObj, Map<SootField, Set<HeapObj>>> heap = new HashMap<>();

    public void addStack(Local local, HeapObj obj) {
        stack.computeIfAbsent(local, k -> new HashSet<>()).add(obj);
    }

    public void addHeap(HeapObj heapObj, SootField field, HeapObj obj) {
        heap.computeIfAbsent(heapObj, k -> new HashMap<>())
                .computeIfAbsent(field, k -> new HashSet<>())
                .add(obj);
    }

    public Set<HeapObj> getStack(Local local) {
        return stack.getOrDefault(local, new HashSet<>());
    }

    public Set<HeapObj> getHeap(HeapObj heapObj, SootField field) {
        return heap.getOrDefault(heapObj, new HashMap<>())
                .getOrDefault(field, new HashSet<>());
    }

    Set<HeapObj> reachable(Local x) {
        Set<HeapObj> visited = new HashSet<>();
        Queue<HeapObj> queue = new LinkedList<>();

        if (stack.containsKey(x)) {
            queue.addAll(stack.get(x));
            visited.addAll(stack.get(x));
        }

        // BFS to find all reachable objects
        while (!queue.isEmpty()) {
            HeapObj current = queue.poll();
            if (heap.containsKey(current)) {
                for (Map.Entry<SootField, Set<HeapObj>> entry : heap.get(current).entrySet()) {
                    for (HeapObj neighbor : entry.getValue()) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        return visited;
    }

    void merge(PTG other) {
        // Merge stack
        for (Map.Entry<Local, Set<HeapObj>> entry : other.stack.entrySet()) {
            Local local = entry.getKey();
            Set<HeapObj> objs = entry.getValue();
            stack.computeIfAbsent(local, k -> new HashSet<>()).addAll(objs);
        }

        // Merge heap
        for (Map.Entry<HeapObj, Map<SootField, Set<HeapObj>>> entry : other.heap.entrySet()) {
            HeapObj heapObj = entry.getKey();
            Map<SootField, Set<HeapObj>> fields = entry.getValue();
            Map<SootField, Set<HeapObj>> thisFields = heap.computeIfAbsent(heapObj, k -> new HashMap<>());

            for (Map.Entry<SootField, Set<HeapObj>> fieldEntry : fields.entrySet()) {
                SootField field = fieldEntry.getKey();
                Set<HeapObj> objs = fieldEntry.getValue();
                thisFields.computeIfAbsent(field, k -> new HashSet<>()).addAll(objs);
            }
        }
    }

    PTG copy() {
        PTG newPTG = new PTG();
        for (Map.Entry<Local, Set<HeapObj>> entry : stack.entrySet()) {
            newPTG.stack.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        for (Map.Entry<HeapObj, Map<SootField, Set<HeapObj>>> entry : heap.entrySet()) {
            Map<SootField, Set<HeapObj>> newFields = new HashMap<>();
            for (Map.Entry<SootField, Set<HeapObj>> fieldEntry : entry.getValue().entrySet()) {
                newFields.put(fieldEntry.getKey(), new HashSet<>(fieldEntry.getValue()));
            }
            newPTG.heap.put(entry.getKey(), newFields);
        }
        return newPTG;
    }

    // print function for ptg which prints stack and heap in a readable format
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Stack:\n");
        for (Map.Entry<Local, Set<HeapObj>> entry : stack.entrySet())
            sb.append("  ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        sb.append("Heap:\n");
        for (Map.Entry<HeapObj, Map<SootField, Set<HeapObj>>>
                entry : heap.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(":\n");
            for (Map.Entry<SootField, Set<HeapObj>> fieldEntry : entry.getValue().entrySet()) {
                sb.append("    ").append(fieldEntry.getKey()).append(" -> ").append(fieldEntry.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PTG)) return false;
        PTG other = (PTG) o;
        return this.stack.equals(other.stack) && this.heap.equals(other.heap);
    }
}

class MethodAnalysis {
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

public class AnalysisTransformer extends SceneTransformer {

    static CallGraph cg;

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        // Store the call graph once as a static field...
        cg = Scene.v().getCallGraph();

        // This code lets us get the main method, our testcases will only have one start
        // point that is
        // in the Test class.
        var entrypoints = Scene.v().getEntryPoints();
        assert (entrypoints.size() == 1);
        SootMethod entryMethod = entrypoints.get(0);

        handleMainMethod(entryMethod);
    }

    void handleMainMethod(SootMethod myMethod) {
        Map<HeapObj, Boolean> globalResults = new HashMap<>();
        Map<HeapObj, Set<Integer>> globalCallSites = new HashMap<>();

        // track visited methods to avoid re-analysis
        Set<SootMethod> visited = new HashSet<>();
        Queue<SootMethod> worklist = new LinkedList<>();
        worklist.add(myMethod);


        while (!worklist.isEmpty()) {
            SootMethod current = worklist.poll();

            if (visited.contains(current))
                continue;
            if (!current.hasActiveBody())
                continue;
            if (!current.getDeclaringClass().isApplicationClass())
                continue;

            visited.add(current);

            MethodAnalysis analysis = new MethodAnalysis();
            analysis.method = current;
            analysis.body = current.getActiveBody();
            analysis.cg = cg;
            analysis.scalarReplaceable = globalResults; // shared!

            analysis.analyzeScalarReplaceability();

            // merge call sites into global map
            for (Map.Entry<HeapObj, Set<Integer>> e : analysis.objCallSites.entrySet()) {
                globalCallSites.computeIfAbsent(e.getKey(), k -> new TreeSet<>())
                        .addAll(e.getValue());
            }

            // add all callees to worklist
            for (Unit u : current.getActiveBody().getUnits()) {
                Stmt stmt = (Stmt) u;
                if (stmt.containsInvokeExpr()) {
                    Iterator<Edge> targets = cg.edgesOutOf(stmt);
                    while (targets.hasNext()) {
                        SootMethod callee = targets.next().tgt();
                        if (!visited.contains(callee)) {
                            worklist.add(callee);
                        }
                    }
                }
            }
        }

        // print results
        globalResults.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        a.getKey().alloc_site.getJavaSourceStartLineNumber(),
                        b.getKey().alloc_site.getJavaSourceStartLineNumber()))
                .forEach(entry -> {
                    int allocLine = entry.getKey().alloc_site.getJavaSourceStartLineNumber();
                    if (entry.getValue()) {
                        Set<Integer> sites = globalCallSites.get(entry.getKey());
                        if (sites == null || sites.isEmpty()) {
                            System.out.println("O" + allocLine + " = Y[]");
                        } else {
                            String lines = sites.stream()
                                    .map(String::valueOf)
                                    .collect(java.util.stream.Collectors.joining(", "));
                            System.out.println("O" + allocLine + " = Y[" + lines + "]");
                        }
                    } else {
                        System.out.println("O" + allocLine + " = N");
                    }
                });
    }
}
