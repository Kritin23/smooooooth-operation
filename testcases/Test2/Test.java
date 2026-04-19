/**
 * Can inline alsmost all (expect for 4 targets)
 * sanity check testcase
 */

abstract interface Worker {
    int work(int x);
}

class A implements Worker {
    public int work(int x) {
        return x + 1;
    }
}

class B implements Worker {
    public int work(int x) {
        return x * 2;
    }
}

class C implements Worker {
    public int work(int x) {
        return x - 3;
    }
}

class D implements Worker {
    public int work(int x) {
        return x + 20;
    }
}

class Util {
    public static int recursive(int n) {
        if (n <= 1) return 1;
        return n * recursive(n - 1);
    }
}

public class Test {

    static Worker getWorker(int i) {
        if (i % 3 == 0) return new A();
        if (i % 3 == 1) return new B();
        return new C();
    }

    static Worker simpleGet() {
        return new B();
    }


    static int one() {
        Worker w = simpleGet();   
        return w.work(10);
    }

    static int two(int i) {
        Worker w;
        if (i % 2 == 0) w = new A();
        else w = new B();     
        return w.work(i);
    }

    static int three(int i) {
        Worker w = getWorker(i);  
        return w.work(i);
    }

    static int four(int i) {
        Worker w;
        if(i % 4 == 0) w = new D();
        else w = getWorker(i);
        return w.work(i);
    }
    

    public static void main(String[] args) {

        long start = System.nanoTime();

        int sum = 0;

        for (int i = 0; i < 1_000_000; i++) {

            // should become fully static after your analysis
            sum += one();

            // maybe split into 2 contexts
            sum += two(i);

            // hardest case
            sum += three(i);

            // recursion stress (inlining depth)
            sum += Util.recursive(5);
        }

        long end = System.nanoTime();

        System.out.println("Result: " + sum);
        System.out.println("Time (us): " + (end - start) / 1_000);
    }
}