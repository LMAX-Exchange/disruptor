package com.lmax.disruptor;

import java.util.Random;

public class Randoms
{

    static long[] generateArray(Random r1, long[] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = r1.nextLong();
        }
        
        return values;
    }

    static int[] generateArray(Random r1, int[] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = r1.nextInt();
        }
        
        return values;
    }

    public static byte[][] generateArray(Random r, byte[][] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            r.nextBytes(values[i]);
        }
        
        return values;
    }
}
