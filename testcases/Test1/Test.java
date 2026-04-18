class M {
    int x, y;
    M m;

    void foo(M mm) {
        m.bar(mm);
    }

    void bar(M mm) {
        mm.m.x = 2;
    }
}

class N extends M {
    void foo(M mm)
    {
        bar(mm);
    }

    void bar(M mm)
    {
        mm.m.x = 1;
    }
}

class T extends N {
    void bar(M mm)
    {
        mm.m.x = 0;
    }
}

public class Test {
    public static void main(String[] args) {
        M x = new M(); // O13
        M y = new M(); // O14
        if (args.length > 0)
            x = new N();
        if(args.length > 1)
            x = new T();
        y.m = x;
        x.foo(y);
        y.foo(x);

    }
}
