/**
 * Long call chain
 * Everything inlined
 * huge improvement
 */


abstract class A {
    abstract int f(int x);
}

class B extends A {
    @Override
    int f(int x) {
        return g1(x);
    }

    int g1(int x) { return g2(x + 1); }
    int g2(int x) { return g3(x + 1); }
    int g3(int x) { return g4(x + 1); }
    int g4(int x) { return g5(x + 1); }
    int g5(int x) { return g6(x + 1); }
    int g6(int x) { return g7(x + 1); }
    int g7(int x) { return g8(x + 1); }
    int g8(int x) { return g9(x + 1); }
    int g9(int x) { return g10(x + 1); }
    int g10(int x) { return g11(x + 1); }
    int g11(int x) { return g12(x + 1); }
    int g12(int x) { return g13(x + 1); }
    int g13(int x) { return g14(x + 1); }
    int g14(int x) { return g15(x + 1); }
    int g15(int x) { return g16(x + 1); }
    int g16(int x) { return g17(x + 1); }
    int g17(int x) { return g18(x + 1); }
    int g18(int x) { return g19(x + 1); }
    int g19(int x) { return g20(x + 1); }

    int g20(int x) {
        return x;
    }
}

class C extends A {
    @Override
    int f(int x) {
        return h1(x);
    }

    int h1(int x) { return h2(x * 2); }
    int h2(int x) { return h3(x * 2); }
    int h3(int x) { return h4(x * 2); }
    int h4(int x) { return h5(x * 2); }
    int h5(int x) { return h6(x * 2); }
    int h6(int x) { return h7(x * 2); }
    int h7(int x) { return h8(x * 2); }
    int h8(int x) { return h9(x * 2); }
    int h9(int x) { return h10(x * 2); }
    int h10(int x) { return x; }
}

public class Test {

    static int run(A a, int x) {
        return a.f(x);
    }

    public static void main(String[] args) {
        A a1 = new B();
        A a2;

        if (args.length > 0) {
            a2 = new B();
        } else {
            a2 = new C();
        }

        long start = System.nanoTime();

        int result = 0;
        for (int i = 0; i < 1000000; i++) {
            result += run(a1, i);
            result += run(a2, i);
        }

        long end = System.nanoTime();

        System.out.println("Result: " + result);
        System.out.println("Time (ms): " + (end - start) / 1000);
    }
}