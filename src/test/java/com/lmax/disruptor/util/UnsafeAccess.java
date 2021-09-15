package com.lmax.disruptor.util;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

public class UnsafeAccess
{
    private static final sun.misc.Unsafe THE_UNSAFE;

    static
    {
        try
        {
            final PrivilegedExceptionAction<sun.misc.Unsafe> action = () ->
            {
                Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (sun.misc.Unsafe) theUnsafe.get(null);
            };

            THE_UNSAFE = AccessController.doPrivileged(action);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unable to load unsafe", e);
        }
    }

    /**
     * Get a handle on the Unsafe instance, used for accessing low-level concurrency
     * and memory constructs.
     *
     * @return The Unsafe
     */
    public static sun.misc.Unsafe getUnsafe()
    {
        return THE_UNSAFE;
    }
}
