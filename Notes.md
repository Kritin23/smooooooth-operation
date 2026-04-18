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
through what I did and laugh at myself. Gautam, if you read this, I hope you 
laugh too.

 - I think I'm implementing an intraprocedural context sensitive analysis.
 Might later try to extend it to interprocedural analysis

 - Maybe don't need full object sensitivity. Mayble only type + param type 
 sensitivity

 - I perform Context Sensitive Analysis, i.e. each method will be analysed in
 the context of its reviever (and possibly param) object types. Store results  for each call site, what all methods can be called and in what contexts.

 - Optimizations -> if a single method -- inline it. if a single context -- 
 make it a specialized and inline it. If small number -- explore callsite 
 splitting

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