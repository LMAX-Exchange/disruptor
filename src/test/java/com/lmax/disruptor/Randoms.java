package com.lmax.disruptor;

import java.util.Random;

public class Randoms
{
    public static long[] generateArray(Random r1, long[] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = r1.nextLong();
        }
        
        return values;
    }

    public static int[] generateArray(Random r1, int[] values)
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

    public static double[] generateArray(Random r, double[] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = r.nextDouble();
        }
        
        return values;
    }

    public static float[] generateArray(Random r, float[] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = r.nextFloat();
        }
        
        return values;
    }

    public static short[] generateArray(Random r, short[] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = (short) r.nextInt();
        }
        
        return values;
    }

    public static char[] generateArray(Random r, char[] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = (char) r.nextInt();
        }
        
        return values;
    }

    public static byte[] generateArray(Random r, byte[] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = (byte) r.nextInt();
        }
        
        return values;
    }
}
