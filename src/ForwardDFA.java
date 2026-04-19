/* ForwardDFA.java */
import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;


public class ForwardDFA {
    // Map<Context, MethodAnalysis> analyses;
    Map<Context, PTG> inPTGs = new HashMap<>();
    // Map<Context, PTG> outPTGs;
    Map<Context, Set<Context>> callers = new HashMap<>();
    Map<Context, List<Set<HeapObj>>> paramMap = new HashMap<>();
    Map<Context, MethodAnalysis.AnalysisInfo> infos = new HashMap<>();
    Queue<Context> analysisQueue = new ArrayDeque<>();
    
    CallGraph cg;

    ForwardDFA(SootMethod entry, CallGraph cg)
    {
        this.cg = cg;


        ArrayList<Set<HeapObj>> p = new ArrayList<>();
        Set<HeapObj> s= new HashSet<>();
        p.add(s);
        analysisQueue.add(
            new Context(entry, null)
        );
    }

    public void runAnalysis()
    {
        while(!analysisQueue.isEmpty())
        {
            // System.out.println("QUeue: " + analysisQueue);
            Context ctx = analysisQueue.poll();

            System.out.println("Running Analysis on " + ctx);

            PTG inPtg = inPTGs.getOrDefault(ctx, new PTG());

            List<Set<HeapObj>> params = paramMap.computeIfAbsent(ctx, 
                (k) -> {
                    int numArgs = k.sm.getParameterCount();
                    List<Set<HeapObj>> p = new ArrayList<>();
                    for(int i=0;i<numArgs;i++)
                            p.add(new HashSet<>());
                    return p;
                }
            );

            MethodAnalysis ma = new MethodAnalysis(
                ctx.sm,
                cg,
                ctx.recv, 
                params,
                inPtg,
                this
            );
            MethodAnalysis.AnalysisInfo oldInfo = infos.computeIfAbsent(ctx, 
                k -> new MethodAnalysis.AnalysisInfo()
            );
            // PTG oldOut = 
            // PTG oldOut = outPTGs.getOrDefault(ctx, new PTG());
            MethodAnalysis.AnalysisInfo info = ma.runAnalysis();
            // inPTGs.put(ctx, inPtg);
            infos.put(ctx, info);
            
            if(!oldInfo.equals(info)) {
                if(callers.containsKey(ctx))
                {
                    for(var callerCtx : callers.get(ctx))
                    {
                        analysisQueue.add(new Context
                            (callerCtx.sm, callerCtx.recv));
                    }
                }
            }
            // System.out.println("QUeue: " + analysisQueue);
            // System.out.println(analysisQueue);
        }
    }

    public MethodAnalysis.AnalysisInfo queueMethod(SootMethod sm,
             HeapObj recv, 
             List<Set<HeapObj>> params, 
             PTG in,
            Context caller)
    {
        Context ctx = new Context(sm, recv);

        // System.out.println("Queuing " + ctx);

        Set<HeapObj> allParams = new HashSet<>();
        for(var l : params) allParams.addAll(l);

        PTG newIn = in.copy_restrict(allParams);
        PTG oldIn = inPTGs.get(ctx);
        newIn.merge(oldIn != null ? oldIn : new PTG());

        if(newIn.equals(oldIn))
        {
            return infos.computeIfAbsent(ctx, k -> new MethodAnalysis.AnalysisInfo());
        }   
        inPTGs.put(ctx, newIn);
        List<Set<HeapObj>> paramList = paramMap.
                        computeIfAbsent(ctx, k -> new ArrayList<>());
        for(int i=0;i<params.size();i++)
        {
            if(paramList.size() <= i)
                    paramList.add(new HashSet<>());
            paramList.get(i)
                .addAll(params.get(i));
        }
        Set<Context> callerSet = callers.computeIfAbsent(ctx,k -> new HashSet<>());
        callerSet.add(caller);
        callers.put(ctx, callerSet);

        if(!analysisQueue.contains(ctx))
            analysisQueue.add(ctx);
        return infos.computeIfAbsent(ctx, k -> new MethodAnalysis.AnalysisInfo());
    }



}
