package com.lmax.disruptor;

import static com.lmax.disruptor.util.Util.getMinimumSequence;

import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.util.MutableLong;
import com.lmax.disruptor.util.PaddedAtomicLong;

public abstract class AbstractMultithreadedClaimStrategy implements ClaimStrategy
{
    private final int bufferSize;
    private final PaddedAtomicLong claimSequence = new PaddedAtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
    private final ThreadLocal<MutableLong> minGatingSequenceThreadLocal = new ThreadLocal<MutableLong>()
    {
        @Override
        protected MutableLong initialValue()
        {
            return new MutableLong(Sequencer.INITIAL_CURSOR_VALUE);
        }
    };

    public AbstractMultithreadedClaimStrategy(int bufferSize)
    {
        this.bufferSize = bufferSize;
    }

    @Override
    public int getBufferSize()
    {
        return bufferSize;
    }

    @Override
    public long getSequence()
    {
        return claimSequence.get();
    }

    @Override
    public boolean hasAvailableCapacity(final int availableCapacity, final Sequence[] dependentSequences)
    {
        return hasAvailableCapacity(claimSequence.get(), availableCapacity, dependentSequences);
    }

    @Override
    public long incrementAndGet(final Sequence[] dependentSequences)
    {
        final MutableLong minGatingSequence = minGatingSequenceThreadLocal.get();
        waitForCapacity(dependentSequences, minGatingSequence);
    
        final long nextSequence = claimSequence.incrementAndGet();
        waitForFreeSlotAt(nextSequence, dependentSequences, minGatingSequence);
    
        return nextSequence;
    }

    @Override
    public long checkAndIncrement(int availableCapacity, int delta, Sequence[] gatingSequences) throws InsufficientCapacityException
    {
        for (;;)
        {
            long sequence = claimSequence.get();
            if (hasAvailableCapacity(sequence, availableCapacity, gatingSequences))
            {
                long nextSequence = sequence + delta;
                if (claimSequence.compareAndSet(sequence, nextSequence))
                {
                    return nextSequence;
                }
            }
            else
            {
                throw InsufficientCapacityException.INSTANCE;
            }
        }
    }

    @Override
    public long incrementAndGet(final int delta, final Sequence[] dependentSequences)
    {
        final long nextSequence = claimSequence.addAndGet(delta);
        waitForFreeSlotAt(nextSequence, dependentSequences, minGatingSequenceThreadLocal.get());
    
        return nextSequence;
    }

    @Override
    public void setSequence(final long sequence, final Sequence[] dependentSequences)
    {
        claimSequence.lazySet(sequence);
        waitForFreeSlotAt(sequence, dependentSequences, minGatingSequenceThreadLocal.get());
    }

    private void waitForCapacity(final Sequence[] dependentSequences, final MutableLong minGatingSequence)
    {
        final long wrapPoint = (claimSequence.get() + 1L) - bufferSize;
        if (wrapPoint > minGatingSequence.get())
        {
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
            {
                LockSupport.parkNanos(1L);
            }
    
            minGatingSequence.set(minSequence);
        }
    }

    private void waitForFreeSlotAt(final long sequence, final Sequence[] dependentSequences, final MutableLong minGatingSequence)
    {
        final long wrapPoint = sequence - bufferSize;
        if (wrapPoint > minGatingSequence.get())
        {
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
            {
                LockSupport.parkNanos(1L);
            }
    
            minGatingSequence.set(minSequence);
        }
    }

    private boolean hasAvailableCapacity(long sequence, final int availableCapacity, final Sequence[] dependentSequences)
    {
        final long wrapPoint = (sequence + availableCapacity) - bufferSize;
        final MutableLong minGatingSequence = minGatingSequenceThreadLocal.get();
        if (wrapPoint > minGatingSequence.get())
        {
            long minSequence = getMinimumSequence(dependentSequences);
            minGatingSequence.set(minSequence);
    
            if (wrapPoint > minSequence)
            {
                return false;
            }
        }
    
        return true;
    }
}