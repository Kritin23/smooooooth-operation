# Team Notes

## TODOS

1) depending on parameter/receiver type -> we keep inlined copies
2) recursive -> bounded we are parsing limited number of times
3) function x receiver x param type  -> optimized body (receiver -> obj, param -> type)
4) receiver -> field types
4) return object you store
5) params store 

## What I'm Doing

I have no idea what I'm doing. I'm just guessing what analysis I'll need to 
perform optimizations. I don't know if I'll be able to implement that. Still, 
I'll give it a shot. While doing that, I'll try to keep this log to later go
through what I did and laugh at myself. Quasar-iwnl, if you read this, I hope 
you laugh too.

 - I think I'm implementing an intraprocedural context sensitive analysis.
 Might later try to extend it to interprocedural analysis

 - Maybe don't need full object sensitivity. Mayble only type + param type 
 sensitivity

 - I perform Context Sensitive Analysis, i.e. each method will be analysed in
 the context of its reviever (and possibly param) object types. Store results 
  for each call site, what all methods can be called and in what contexts.

 - Optimizations -> if a single method -- inline it. if a single context -- 
 make it a specialized and inline it. If small number -- explore callsite 
splitting

 - I'm trying to make the analysis intraprocedural. For this, I need to handle
 return statements, and make modifications to the worklist algorithm.

 - In case of indirect/direct recursive calls, same function's analysis in 
 the same context gets called, returning an empty PTG. But later, when its 
 actual PTG is computed, the analysis of the caller needs to be run again. 

 - I'm thinking a global map of Context -> MethodAnalysisObject. When some 
 information gets added, all its consumers are added to the queue to be 
 analysed again. I will need to store call sites as well then. 

 - I think a separate class which manages the method worklist and keeps track
 of PTGs, analysis output PTGs and caller-callee maps. This class will start
 a method's callers' analysis whenever new info is received

 - Needed to add a Static type cast before inlining or else JVM would cry 
  ``` Java
		a.foo(); // a is statically known to be of type B

		// this converts to 

		temp = (B) a;
		temp.foo();

		// now temp.foo() is inlined
  ```

  - Exploring call site splitting now. Hopefully the last thing I'll do here 
  ``` Java
	a.foo(); // static analysis gives two targets - X::foo() and Y::foo()

	// change this to 

	if(a instanceof X) {
		temp = (X) a;
		temp.(X::foo)() // inlined
	} else if ( a instance of Y) {
		temp = (Y) a;
		temp.(Y::foo)() // inlined 
	}
	else {
		a.foo();  // safety
	}
  ```
## Observations and Potential Testcases

a.b.f

a.f -> has c.g
b.f -> has c.g

merge ptgs whener 
a->o1
a.f multiple calls
merge

foo1 foo2 foo3

``` Java
foo1() {
a.c = new obj;
a.f()
a.c = new obj
a.f()
a.c = new obj
a.f()
// how do we update ptg in this case
// merge all ptgs and give it to context because a is annoying

}
f {
	c.bar();
} 
```