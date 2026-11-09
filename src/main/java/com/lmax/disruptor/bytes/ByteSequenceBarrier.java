/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor.bytes;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;

/**
 * {@link SequenceBarrier} handed out for gating {@link ByteRecordProcessor}s on the cursor of a {@link ByteRingBuffer}
 * and optional dependent processors, using the given WaitStrategy.
 */
final class ByteSequenceBarrier implements SequenceBarrier
{
    private final WaitStrategy waitStrategy;
    private final Sequence cursor;
    private final Sequence dependentSequence;
    private volatile boolean alerted = false;

    ByteSequenceBarrier(final WaitStrategy waitStrategy, final Sequence cursor, final Sequence dependentSequence)
    {
        this.waitStrategy = waitStrategy;
        this.cursor = cursor;
        this.dependentSequence = dependentSequence;
    }

    @Override
    public long waitFor(final long position) throws AlertException, InterruptedException, TimeoutException
    {
        checkAlert();
        return waitStrategy.waitFor(position, cursor, dependentSequence, this);
    }

    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert()
    {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException
    {
        if (alerted)
        {
            throw AlertException.INSTANCE;
        }
    }
}
