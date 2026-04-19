/**
 * We have a very large polymorphic call
 * Don't expect any optimization
 */

abstract class A {
    abstract int foo();
}

class B extends A {
    int foo() {
        return 1;
    }
}

class C extends A {
    int foo() {
        return 2;
    }
}

class D extends A {
    int foo() {
        return 3;
    }
}

class E extends A {
    int foo() {
        return 4;
    }
}

class F extends A {
    int foo() {
        return 5;
    }
}

class G extends A {
    int foo() {
        return 6;
    }
}

class H extends A {
    int foo() {
        return 7;
    }
}

class I extends A {
    int foo() {
        return 8;
    }
}

class J extends A {
    int foo() {
        return 9;
    }
}

public class Test {

    static A merge(A a1, A a2) {
        if (a1.hashCode() % 2 == 0)
            return a1;
        else
            return a2;
    }

    public static void main(String[] args) {
        int sum = 0;

        long start = System.nanoTime();
        for (int j = 0; j < 100000; j++) {
            A a = new B(); // initial value
            A obj;
            for (int i = 0; i < 100; i++) {
                switch (i % 9) {
                    case 0:
                        obj = new B();
                    case 1:
                        obj = new C();
                    case 2:
                        obj = new D();
                    case 3:
                        obj = new E();
                    case 4:
                        obj = new F();
                    case 5:
                        obj = new G();
                    case 6:
                        obj = new H();
                    case 7:
                        obj = new I();
                    default:
                        obj = new J();
                }

                // Merge destroys precision
                a = merge(a, obj);

                sum += a.foo();
            }

        }
        long end = System.nanoTime();
        System.out.println("Result: " + sum);
        System.out.println("Time : " + (end - start) / 1000);
    }
}
