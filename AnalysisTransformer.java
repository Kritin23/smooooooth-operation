import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

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
