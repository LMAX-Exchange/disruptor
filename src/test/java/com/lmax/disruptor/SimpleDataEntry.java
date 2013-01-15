package com.lmax.disruptor;

import com.lmax.disruptor.util.Bits;

public class SimpleDataEntry extends RingBufferEntryBase implements SimpleData
{
    private static final int LENGTH_OFFSET            = BASE_OFFSET;
    private static final int PREVIOUS_SEQUENCE_OFFSET = LENGTH_OFFSET + Bits.sizeofInt();
    private static final int DATA_LENGTH_OFFSET       = PREVIOUS_SEQUENCE_OFFSET + Bits.sizeofLong();
    private static final int DATA_OFFSET              = DATA_LENGTH_OFFSET + Bits.sizeofInt();
    
    public static final EntryFactory<SimpleData> FACTORY = new EntryFactory<SimpleData>()
    {
        @Override
        public SimpleData newInstance()
        {
            return new SimpleDataEntry();
        }
    };
    
    @Override
    public int size()
    {
        return DATA_LENGTH_OFFSET + getDataLength();
    }

    @Override
    public int getLength()
    {
        return memory.getInt(reference, LENGTH_OFFSET);
    }

    @Override
    public void setLength(int value)
    {
        memory.putInt(reference, LENGTH_OFFSET, value);
    }

    @Override
    public long getPreviousSequence()
    {
        return memory.getLong(reference, PREVIOUS_SEQUENCE_OFFSET);
    }

    @Override
    public void setPreviousSequence(long value)
    {
        memory.putLong(reference, PREVIOUS_SEQUENCE_OFFSET, value);
    }

    @Override
    public int getDataLength()
    {
        return memory.getInt(reference, DATA_LENGTH_OFFSET);
    }

    @Override
    public void setDataLength(int value)
    {
        memory.putInt(reference, DATA_LENGTH_OFFSET, value);
    }

    @Override
    public byte[] getData()
    {
        return memory.getBytes(reference, DATA_OFFSET, getDataLength());
    }

    @Override
    public int setData(byte[] value, int offset, int length)
    {
        return memory.putBytes(reference, DATA_OFFSET, value, offset, length);
    }    
}
