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
package com.lmax.disruptor.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;

public final class TestWaiter implements Callable<List<StubEvent>>
{
    private final long toWaitForSequence;
    private final long initialSequence;
    private final CyclicBarrier cyclicBarrier;
    private final SequenceBarrier sequenceBarrier;
    private final RingBuffer<StubEvent> ringBuffer;

    public TestWaiter(
        final CyclicBarrier cyclicBarrier,
        final SequenceBarrier sequenceBarrier,
        final RingBuffer<StubEvent> ringBuffer,
        final long initialSequence,
        final long toWaitForSequence)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.initialSequence = initialSequence;
        this.ringBuffer = ringBuffer;
        this.toWaitForSequence = toWaitForSequence;
        this.sequenceBarrier = sequenceBarrier;
    }

    @Override
    public List<StubEvent> call() throws Exception
    {
        cyclicBarrier.await();
        sequenceBarrier.waitFor(toWaitForSequence);

        final List<StubEvent> messages = new ArrayList<StubEvent>();
        for (long l = initialSequence; l <= toWaitForSequence; l++)
        {
            messages.add(ringBuffer.get(l));
        }

        return messages;
    }
}