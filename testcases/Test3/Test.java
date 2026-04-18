abstract class A {
    abstract int f();
}

class B extends A {
    int v;

    B(int v) {
        this.v = v;
    }

    @Override
    int f() {
        return v;
    }
}

abstract class C extends A {
    A l, r;

    C(A l, A r) {
        this.l = l;
        this.r = r;
    }
}

class D extends C {
    D(A l, A r) {
        super(l, r);
    }

    @Override
    int f() {
        return l.f() + r.f();
    }
}

class E extends C {
    E(A l, A r) {
        super(l, r);
    }

    @Override
    int f() {
        return l.f() * r.f();
    }
}

class F extends A {
    A c;

    F(A c) {
        this.c = c;
    }

    @Override
    int f() {
        return -c.f();
    }
}

public class Test {

    static int g(A n) {
        return n.f();
    }

    public static void main(String[] args) {
        int a=0,b=0;
        long start = System.nanoTime();
        for(int i=0;i<1000000;i++)
        {
            A b1 = new B(2);
            A b2 = new B(3);
            A e1 = new E(b1, b2);
            A b3 = new B(1);
            A t1 = new D(b3, e1);

            A b4 = new B(4);
            A b5 = new B(5);
            A d2 = new D(b4, b5);
            A t2 = new F(d2);

            a = g(t1);
            b = g(t2);
        }
        System.out.println(a);
        System.out.println(b);
        long end = System.nanoTime();


        System.out.println("Time (us): " + (end - start) / 1_000);
    }
}