/**
 * Simple testcase. expect large speedup
 */

class A {
    int add(int a, int b)
    {
        return a+b;
    }
}

public class Test {
    public static void main(String[] args) {
        int res = 0;
        long start = System.nanoTime();

        A a = new A();
        for(int i=0;i<10000000;i++)
        {
            res += a.add(res, i);
        }

        long end = System.nanoTime();
        System.out.println("Result: " + res);
        System.out.println("Time: " + (end - start) / 1000);

    }
}
