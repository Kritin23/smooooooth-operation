/**
 * We don't handle recursive calls
 * Negative Testcase
 */

class A {
    public A f;   

    public void foo(int i)
    {
        Test.ctr++;
        if (i > 0)
        {
            f = new A();
            foo(i-1);
        }
    }
}

public class Test {
    static int ctr;

    public static void main(String[] args)
    {
        long start = System.nanoTime();
        for(int i=0;i<10000;i++)
        {
            A b = new A();
            b.foo(1000);
        }
        long stop = System.nanoTime();


        System.out.println("Result: " + ctr);
        System.out.println("Time: " + (stop - start) / 1000);

    }
}