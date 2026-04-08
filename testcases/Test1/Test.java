class M {
    int x, y;
    M m;

    void foo(M mm) {
        bar(mm);
    }

    void bar(M mm) {
        mm.m.x = 2;
    }
}

public class Test {
    public static void main(String[] args) {
        M x = new M(); // O13
        M y = new M(); // O14
        x.foo(y);
    }
}
