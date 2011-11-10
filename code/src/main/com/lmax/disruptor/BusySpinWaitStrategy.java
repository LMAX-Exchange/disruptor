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
package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.util.Util.getMinimumSequence;

/**
 * Busy Spin strategy that uses a busy spin loop for {@link com.lmax.disruptor.EventProcessor}s waiting on a barrier.
 *
 * This strategy will use CPU resource to avoid syscalls which can introduce latency jitter.  It is best
 * used when threads can be bound to specific CPU cores.
 */
public final class BusySpinWaitStrategy implements WaitStrategy
{
    @Override
    public long waitFor(final long sequence, final Sequence cursor, final Sequence[] dependents, final SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;

        if (0 == dependents.length)
        {
            while ((availableSequence = cursor.get()) < sequence)
            {
                barrier.checkAlert();
            }
        }
        else
        {
            while ((availableSequence = getMinimumSequence(dependents)) < sequence)
            {
                barrier.checkAlert();
            }
        }

        return availableSequence;
    }

    @Override
    public long waitFor(final long sequence, final Sequence cursor, final Sequence[] dependents, final SequenceBarrier barrier,
                        final long timeout, final TimeUnit sourceUnit)
        throws AlertException, InterruptedException
    {
        final long timeoutMs = sourceUnit.toMillis(timeout);
        final long startTime = System.currentTimeMillis();
        long availableSequence;

        if (0 == dependents.length)
        {
            while ((availableSequence = cursor.get()) < sequence)
            {
                barrier.checkAlert();

                final long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > timeoutMs)
                {
                    break;
                }
            }
        }
        else
        {
            while ((availableSequence = getMinimumSequence(dependents)) < sequence)
            {
                barrier.checkAlert();

                final long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > timeoutMs)
                {
                    break;
                }
            }
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
    }
}
