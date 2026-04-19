import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;




public class PTG {
    public Map<Local, Set<HeapObj>> stack = new HashMap<>();
    public Map<HeapObj, Map<SootField, Set<HeapObj>>> heap = new HashMap<>();

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

    Set<HeapObj> reachable(HeapObj o)
    {
        Set<HeapObj> visited = new HashSet<>();
        Queue<HeapObj> queue = new LinkedList<>();

        queue.add(o);
        visited.add(o);

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
        // System.out.println("merging");
        for (Map.Entry<Local, Set<HeapObj>> entry : other.stack.entrySet()) {
            Local local = entry.getKey();
            Set<HeapObj> objs = entry.getValue();
            Set<HeapObj> newSet = stack.computeIfAbsent(local, k -> new HashSet<>());
            newSet.addAll(objs);
            stack.put(local, newSet);

        }

        // Merge heap
        // Set<HeapObj> objSet = heap.keySet();
        for (HeapObj o : other.heap.keySet())
        {
            // System.out.println("adding " + o);
            Map<SootField, Set<HeapObj>> map = other.heap.get(o);
            Set<SootField> fields = map.keySet();
            for(var f : fields)
            {
                Set<HeapObj> objs = other.getHeap(o, f);
                for (var pointee : objs)
                    addHeap(o, f, pointee);
            }
        }

        // System.out.println("final " + this);
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

    /**
     * Copies PTG reachable from locals set
     * @param locals
     * @return copied PTG
     */
    PTG copy_restrict(List<Local> locals)
    {
        Set<HeapObj> reachableSet = new HashSet<>();
        for(var l : locals)
        {
            reachableSet.addAll(reachable(l));
        }


        PTG newPTG = new PTG();
        for (var l : locals) {
            if(stack.keySet().contains(l))
                newPTG.stack.put(l, new HashSet<>(stack.get(l)));
        }
        for (Map.Entry<HeapObj, Map<SootField, Set<HeapObj>>> entry : heap.entrySet()) {
            if(reachableSet.contains(entry.getKey())) {
                Map<SootField, Set<HeapObj>> newFields = new HashMap<>();
                for (Map.Entry<SootField, Set<HeapObj>> fieldEntry : entry.getValue().entrySet()) {
                    newFields.put(fieldEntry.getKey(), new HashSet<>(fieldEntry.getValue()));
                }
                newPTG.heap.put(entry.getKey(), newFields);
            }
        }
        return newPTG;
    }

    PTG copy_restrict(Set<HeapObj> base)
    {
        
        PTG newPTG = new PTG();
        Set<HeapObj> reachableSet = new HashSet<>();
        for(var o : base)
        {
            reachableSet.addAll(reachable(o));
        }
        // for (var l : locals) {
        //     if(stack.keySet().contains(l))
        //         newPTG.stack.put(l, new HashSet<>(stack.get(l)));
        // }
        for (Map.Entry<HeapObj, Map<SootField, Set<HeapObj>>> entry : heap.entrySet()) {
            if(reachableSet.contains(entry.getKey())) {
                Map<SootField, Set<HeapObj>> newFields = new HashMap<>();
                for (Map.Entry<SootField, Set<HeapObj>> fieldEntry : entry.getValue().entrySet()) {
                    newFields.put(fieldEntry.getKey(), new HashSet<>(fieldEntry.getValue()));
                }
                newPTG.heap.put(entry.getKey(), newFields);
            }
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
