/**
 * Random testcase
 * Don't really know what will happen
 * Added to check correctness
 */


abstract class A {
    abstract int foo();
}

class B extends A {
    int x;
    B(int x) { this.x = x; }
    int foo() { return x + 1; }
}

class C extends A {
    int x;
    C(int x) { this.x = x; }
    int foo() { return x * 2; }
}

class D extends A {
    int x;
    D(int x) { this.x = x; }
    int foo() { return x - 3; }
}

class E extends A {
    int x;
    E(int x) { this.x = x; }
    int foo() { return x * x; }
}

public class Test {

    static A foo(A a, int i) {
        if (i % 3 == 0) {
            return new B(i);
        } else if (i % 3 == 1) {
            return new C(i);
        } else {
            return a; 
        }
    }

    static A bar(A a, A b, int i) {
        if (i % 2 == 0) return a;
        else return b;
    }

    static int zar(A a, int i) {
        if (i % 4 == 0) {
            return a.foo();
        } else {
            A tmp;
            if (i % 2 == 0) tmp = new D(i);
            else tmp = new E(i);

            A merged = bar(a, tmp, i);
            return merged.foo();
        }
    }

    static A doo(A a, int i) {
        if (i % 5 == 0) {
            return a;
        } else if (i % 5 == 1) {
            return new B(i);
        } else if (i % 5 == 2) {
            return new C(i);
        } else if (i % 5 == 3) {
            return new D(i);
        } else {
            return new E(i);
        }
    }

    public static void main(String[] args) {
        int sum = 0;

        long start = System.nanoTime();
        for (int j = 0; j < 100000; j++) {
            A a = new B(j); 

            for (int i = 0; i < 100; i++) {

                if (i % 3 == 0) {
                    a = foo(a, i);
                } else if (i % 3 == 1) {
                    a = doo(a, i);
                } else {
                    A temp = new C(i);
                    a = bar(a, temp, i);
                }

                sum += zar(a, i);
            }

        }
        long end = System.nanoTime();

        System.out.println("Result: " + sum);
        System.out.println("Time : " + (end - start) / 1000);
    }
}