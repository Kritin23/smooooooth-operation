abstract class A {
    abstract int foo();
}

class B extends A {
    int val;
    B(int v) { this.val = v; }

    int foo() {
        return val + 10;
    }
}

class C extends A {
    int val;
    C(int v) { this.val = v; }

    int foo() {
        return val * 2;
    }
}

class Gen {
    static A make(int type, int seed) {
        if (type == 0) {
            return new B(seed);
        } else {
            return new C(seed);
        }
    }
}


class Gen2 {
    static A build(int x) {
        if (x < 50) {
            return Gen.make(0, x); 
        } else {
            return Gen.make(1, x);
        }
    }
}

public class Test {
    public static void main(String[] args)
    {
        long start = System.nanoTime();
        int res = 0;
        for(int i=0;i<1000;i++)
        {
            A a = Gen2.build(i);
            res += a.foo();
        }

        long stop = System.nanoTime();

        System.out.println("Result: " + res);
        System.out.println("Time: " + (stop - start) / 1000);
        
        
    }
}