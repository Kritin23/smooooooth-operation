/* AnalysisTransformer.java */

import java.util.*;
import java.util.function.BiConsumer;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.invoke.SiteInliner;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

public class AnalysisTransformer extends SceneTransformer {

    static CallGraph cg;

    static int numIters = 1;
    static boolean doBasic = true;
    static boolean doBetter = false;


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

        // dumpJimple();


        Optimization opt = new Optimization(cg);

        for (int i = 0; i < numIters; i++) {
            // Get Fresh Call Graph
            Scene.v().releaseCallGraph();
            Scene.v().releaseReachableMethods();
            PackManager.v().getPack("cg").apply();
            cg = Scene.v().getCallGraph();
            opt.setCG(cg);

            System.out.println("Basic Inlining -----");
            dumpCallGraphDot(cg, "callGraphs/cg_" + i + ".dot");
            if (doBasic)
                opt.basicInlining();
            // forEachMethod((sc, sm) -> cleanupPass(sc, sm));
            // dumpJimple();

            // Get Fresh Call Graph
            Scene.v().releaseCallGraph();
            Scene.v().releaseReachableMethods();
            PackManager.v().getPack("cg").apply();
            cg = Scene.v().getCallGraph();
            opt.setCG(cg);

            System.out.println("Starting Analysis -----");
            handleEntryPoint(entryMethod);
            System.out.println("Ended Analysis -----");

            if(doBetter)
                forEachMethod((sc, sm) -> opt.betterInlining(sc, sm));

            System.out.println("Targets -----");
            MethodAnalysis.printTargets();
            MethodAnalysis.clearAllResults();

            // forEachMethod((sc, sm) -> cleanupPass(sc, sm));

        }
        // dumpJimple();
        outputJimple();
    }

    void cleanupPass(SootClass sc, SootMethod sm) {
        if (!sm.hasActiveBody())
            return;

        Body b = sm.getActiveBody();

        PackManager.v().getPack("jtp").apply(b);
        PackManager.v().getPack("jop").apply(b);

        b.validate();
    }

    void forEachMethod(BiConsumer<SootClass, SootMethod> fn) {
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                fn.accept(sc, sm);
            }
        }
    }

    void dumpJimple() {
        forEachMethod((sc, sm) -> dumpMethodJimple(sc, sm));
    }

    void outputJimple() {
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            String fileName = "./sootJimple/" +
                    sc.getName() + "." + ".jimple";
            try (java.io.PrintWriter out = new java.io.PrintWriter(fileName)) {
                for (SootMethod sm : sc.getMethods()) {
                    if (!sm.hasActiveBody())
                        continue;
                    Body body = sm.getActiveBody();
                    out.println(body);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void dumpMethodJimple(SootClass sc, SootMethod sm) {
        if (!sm.hasActiveBody())
            return;
        System.out.println("Method: " + sc + "::" + sm);
        Body body = sm.getActiveBody();
        System.out.println("{");
        for (Unit u : body.getUnits())
            System.out.println("\t" + u);
        System.out.println("}");
        System.out.println("");
    }

    void handleEntryPoint(SootMethod sm) {
        int numArgs = sm.getParameterCount();
        List<Local> params = new ArrayList<>();
        for (int i = 0; i < numArgs; i++)
            params.add(null);
        MethodAnalysis main = new MethodAnalysis(sm, cg, null, params, new PTG());
        main.runAnalysis();
    }

    void dumpCallGraphDot(CallGraph cg, String file) {
        try (java.io.PrintWriter out = new java.io.PrintWriter(file)) {
            out.println("digraph CG {");

            Iterator<Edge> it = cg.iterator();
            while (it.hasNext()) {
                Edge e = it.next();

                SootMethod src = e.src();
                SootMethod tgt = e.tgt();

                if (src == null || tgt == null)
                    continue;

                if (!src.getDeclaringClass().isApplicationClass() ||
                        !tgt.getDeclaringClass().isApplicationClass())
                    continue;
                String srcName = src.getName();
                String tgtName = tgt.getName();

                out.println("\"" + srcName + "\" -> \"" + tgtName + "\";");
            }

            out.println("}");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * void handleMainMethod(SootMethod myMethod) {
     * Map<HeapObj, Boolean> globalResults = new HashMap<>();
     * Map<HeapObj, Set<Integer>> globalCallSites = new HashMap<>();
     * 
     * // track visited methods to avoid re-analysis
     * Set<SootMethod> visited = new HashSet<>();
     * Queue<SootMethod> worklist = new LinkedList<>();
     * worklist.add(myMethod);
     * 
     * 
     * while (!worklist.isEmpty()) {
     * SootMethod current = worklist.poll();
     * 
     * if (visited.contains(current))
     * continue;
     * if (!current.hasActiveBody())
     * continue;
     * if (!current.getDeclaringClass().isApplicationClass())
     * continue;
     * 
     * visited.add(current);
     * 
     * MethodAnalysis analysis = new MethodAnalysis(current, cg);
     * analysis.method = current;
     * analysis.body = current.getActiveBody();
     * analysis.cg = cg;
     * // analysis.scalarReplaceable = globalResults; // shared!
     * 
     * analysis.runAnalysis();
     * 
     * // merge call sites into global map
     * for (Map.Entry<HeapObj, Set<Integer>> e : analysis.objCallSites.entrySet()) {
     * globalCallSites.computeIfAbsent(e.getKey(), k -> new TreeSet<>())
     * .addAll(e.getValue());
     * }
     * 
     * // add all callees to worklist
     * for (Unit u : current.getActiveBody().getUnits()) {
     * Stmt stmt = (Stmt) u;
     * if (stmt.containsInvokeExpr()) {
     * Iterator<Edge> targets = cg.edgesOutOf(stmt);
     * while (targets.hasNext()) {
     * SootMethod callee = targets.next().tgt();
     * if (!visited.contains(callee)) {
     * worklist.add(callee);
     * }
     * }
     * }
     * }
     * }
     * 
     * // print results
     * globalResults.entrySet().stream()
     * .sorted((a, b) -> Integer.compare(
     * a.getKey().alloc_site.getJavaSourceStartLineNumber(),
     * b.getKey().alloc_site.getJavaSourceStartLineNumber()))
     * .forEach(entry -> {
     * int allocLine = entry.getKey().alloc_site.getJavaSourceStartLineNumber();
     * if (entry.getValue()) {
     * Set<Integer> sites = globalCallSites.get(entry.getKey());
     * if (sites == null || sites.isEmpty()) {
     * System.out.println("O" + allocLine + " = Y[]");
     * } else {
     * String lines = sites.stream()
     * .map(String::valueOf)
     * .collect(java.util.stream.Collectors.joining(", "));
     * System.out.println("O" + allocLine + " = Y[" + lines + "]");
     * }
     * } else {
     * System.out.println("O" + allocLine + " = N");
     * }
     * });
     * }
     * 
     */
}
