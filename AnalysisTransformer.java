import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.invoke.SiteInliner;
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

        inlineBasic();

        handleEntryPoint(entryMethod);
        // handleMainMethod(entryMethod);
    }

    public record inlineTarget(SootMethod inlinee, Stmt site, SootMethod container){}

    void inlineBasic()
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

    void handleEntryPoint(SootMethod sm)
    {
        int numArgs = sm.getParameterCount();
        List<Local> params = new ArrayList<>();
        for(int i=0;i<numArgs;i++)
            params.add(null);
        MethodAnalysis main = new MethodAnalysis(sm, cg, null, params, new PTG());
        main.runAnalysis();
    }

    /* 
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

            MethodAnalysis analysis = new MethodAnalysis(current, cg);
            analysis.method = current;
            analysis.body = current.getActiveBody();
            analysis.cg = cg;
            // analysis.scalarReplaceable = globalResults; // shared!

            analysis.runAnalysis();

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

    */
}
