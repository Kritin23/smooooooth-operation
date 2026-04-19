
class A {
    A f;

    public void foo(A x){
        System.out.println("A-foo");
        f = x;
    }
}

class B extends A {
    public void foo(A x){
        System.out.println("B-foo");
        f = new B();
        f.f = x;
    }
}


public class Test {
    public static void main(String[] args)
    {
        A a = new A();
        a.f = new A();
        a.f.f = new A();     
        a.foo(new B());
        a.f.foo(new B());
        
    }
}