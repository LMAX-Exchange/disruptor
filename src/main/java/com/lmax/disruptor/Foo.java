package com.lmax.disruptor;

/**
 * Created by barkerm on 13/06/17.
 */
public class Foo
{
    int a;
    int b;
    short c;
    short d;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Foo foo = (Foo) o;

        if (a != foo.a) return false;
        if (b != foo.b) return false;
        if (c != foo.c) return false;
        return d == foo.d;
    }

    @Override
    public int hashCode()
    {
        int result = a;
        result = 31 * result + b;
        result = 31 * result + (int) c;
        result = 31 * result + (int) d;
        return result;
    }
}
