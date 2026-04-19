/**
 * Added to check correctness, 
 * highlight some imprecision in out analysis
 */


class A {
    A f;

    public void foo(A x){
        Test.ctr ++;
        f = x;
    }
}

class B extends A {
    public void foo(A x){
        Test.ctr --;
        f = new B();
        f.f = x;
    }
}


public class Test {
    static int ctr = 0;
    public static void main(String[] args)
    {

        long start = System.nanoTime();
        for(int i=0;i<1000000;i++) {
            A a = new A();
            a.f = new A();
            a.f.f = new A();     
            a.foo(new B());
            a.f.foo(new B());
            a.foo(new A());
            
        }
        long stop = System.nanoTime();

        System.out.println("Result: " + Test.ctr);
        System.out.println("Time: " + (stop - start) / 1000);

        
    }
}