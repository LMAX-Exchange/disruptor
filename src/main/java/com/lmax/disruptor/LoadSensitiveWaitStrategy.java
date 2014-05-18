package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;

/**
 * LoadSensitive strategy is the combined strategy that use BusySpin and BlockingWait. 
 * It achieves similar high throughput and low latency as BusySpin Strategy in high load scenarios,
 * while performs like BlockingWait strategy in idle state to save CPU power and system resource.
 * The basic concept is somehow similar with PhasedBackOffWait Strategy, but the performance is better and 
 * easier to use without customizations.
 */
public final class LoadSensitiveWaitStrategy implements WaitStrategy
{
    private static final int DEFAULT_RETRIES = 10;
    
    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();
    private AtomicBoolean waitOnLock = new AtomicBoolean(false); 

    private final int retries;

    public LoadSensitiveWaitStrategy()
    {
        this(DEFAULT_RETRIES);
    }

    public LoadSensitiveWaitStrategy(int retries)
    {
        this.retries = retries;
    }
    
   
    @Override
    public long waitFor(final long sequence, Sequence cursor, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        int counter = retries;

        while ((availableSequence = dependentSequence.get()) < sequence)
        {
        	barrier.checkAlert();

            if (counter > 0)
            {
                --counter;
            }
            else
            {
            	lock.lock();
            	try {
            		waitOnLock.set(true);
            		if ((availableSequence = dependentSequence.get()) >= sequence)
                    {
                        break;
                    }
        			processorNotifyCondition.await();
        		} 
            	finally
            	{
            	   lock.unlock();
            	}           
            }
            
        }
        
        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
    	if(waitOnLock.getAndSet(false)){
	    	lock.lock();
	    	try
		    {
	    		processorNotifyCondition.signalAll();
		    }
		    finally
		    {
		    	lock.unlock();
		    }
    	}
    }
}

