package com.lmax.disruptor;

public class DependentSequence
{
    private Sequence cursor;
    private Sequence[] dependents;

    public DependentSequence(Sequence cursor, Sequence[] dependents)
    {
        this.cursor = cursor;
        this.dependents = dependents;
    }
    
    public long getDependentSequence(long requestedSequence)
    {
        long sequence = cursor.get();
        
        for (Sequence s : dependents)
        {
            sequence = Math.min(sequence, s.get());
            if (sequence < requestedSequence)
            {
                break;
            }
        }
        
        return sequence;
    }
}
