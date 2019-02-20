package com.lmax.disruptor.example;

import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceReportingEventHandler;
import com.lmax.disruptor.support.LongEvent;

public class EarlyReleaseHandler implements SequenceReportingEventHandler<LongEvent>
{
    private Sequence sequenceCallback;
    private int batchRemaining = 20;

    @Override
    public void setSequenceCallback(Sequence sequenceCallback)
    {
        this.sequenceCallback = sequenceCallback;
    }

    @Override
    public void onEvent(LongEvent event, long sequence, boolean endOfBatch) throws Exception
    {
        processEvent(event);

        if (isLogicalChunkOfWorkComplete())
        {
            sequenceCallback.set(sequence);
            batchRemaining = 20;
        }
    }

    private boolean isLogicalChunkOfWorkComplete()
    {
        // Ret true or false based on whatever cirteria is required for the smaller
        // chunk.  If this is doing I/O, it may be after flushing/syncing to disk
        // or at the end of DB batch+commit.
        // Or it could simply be working off a smaller batch size.

        return --batchRemaining == -1;
    }

    private void processEvent(LongEvent event)
    {
        // Do processing
    }
}
