package com.lmax.disruptor.util;

public class Bits
{    
    public static int sizeofByte()
    {
        return 1;
    }
    
    public static int sizeof(byte value)
    {
        return sizeofByte();
    }
    
    public static int sizeofShort()
    {
        return 2;
    }
    
    public static int sizeof(short value)
    {
        return sizeofShort();
    }
    
    public static int sizeofChar()
    {
        return 2;
    }

    public static int sizeof(char value)
    {
        return sizeofChar();
    }
    
    public static int sizeofInt()
    {
        return 4;
    }

    public static int sizeof(int value)
    {
        return sizeofInt();
    }
    
    public static int sizeofFloat()
    {
        return 4;
    }

    public static int sizeof(float value)
    {
        return sizeofFloat();
    }
    
    public static int sizeofLong()
    {
        return 8;
    }

    public static int sizeof(long value)
    {
        return sizeofLong();
    }

    public static int sizeofDouble()
    {
        return 8;
    }

    public static int sizeof(double value)
    {
        return sizeofDouble();
    }
}
