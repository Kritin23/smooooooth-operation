import java.util.*;


class A 
{
    int i;

    static int ctr;

    public A()
    {
        i = ctr++;
    }

    int foo()
    {
        return 2*i;
    }
}

class B extends A  
{
    int j;

    public B()
    {
        j = ctr++;
    }

    int foo()
    {
        return j + i;
    }
}

class C extends A 
{
    int k;

    public C()
    {
        k = ctr++;
    }

    int foo()
    {
        return k*i;
    }

}



public class Test {
    static A getA()
    {
        return new A();
    }    

    static A getB()
    {
        return new B();
    }

    static A getC() 
    {
        return new C();
    }

    static void run1()
    {
        long start = System.nanoTime();

        int res = 0;
        for(int i=0;i<100000;i++)
        {
            res = 0;
            List<A> arr = new ArrayList<>();
            arr.add(getA());
            arr.add(getB());
            arr.add(getC());
            arr.add(getA());
            arr.add(getB());
            arr.add(getC());
            arr.add(getA());
            arr.add(getB());
            arr.add(getC());
            for(var v : arr)
            {
                res += v.foo();
            }
        }

        long stop = System.nanoTime();

        System.out.println("Run1 Result: "+ res);
        System.out.println("Run1 Time: " + (stop - start) / 1000);
    }

    static void run2()
    {
        long start = System.nanoTime();

        int res = 0;
        for(int i=0;i<100000;i++)
        {
            res = 0;
            List<A> arr = new ArrayList<>();
            arr.add(new A());
            arr.add(new B());
            arr.add(new C());
            arr.add(new A());
            arr.add(new B());
            arr.add(new C());
            arr.add(new A());
            arr.add(new B());
            arr.add(new C());
            for(var v : arr)
            {
                res += v.foo();
            }
        }

        long stop = System.nanoTime();

        System.out.println("Run1 Result: "+ res);
        System.out.println("Run1 Time: " + (stop - start) / 1000);
    }

    public static void main(String[] Args)
    {
        run1();
        run2();
    }
}
