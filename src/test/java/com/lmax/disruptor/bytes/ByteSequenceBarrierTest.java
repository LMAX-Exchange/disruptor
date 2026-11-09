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
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ByteSequenceBarrierTest
{
    private final ExecutorService executor = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
    private final ByteRingBuffer ringBuffer = new ByteRingBuffer(256, new BusySpinWaitStrategy());

    public ByteSequenceBarrierTest()
    {
        for (int i = 0; i < 3; i++)
        {
            assertTrue(ringBuffer.write(i, new byte[8], 0, 8));
        }
    }

    @Test
    public void shouldWaitForSlowestDependentSequence() throws Exception
    {
        final Sequence ahead = new Sequence(48L);
        final Sequence behind = new Sequence(32L);
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(ahead, behind);

        assertEquals(32L, sequenceBarrier.waitFor(16L));
    }

    @Test
    public void shouldWaitUntilDependentSequenceReachesPosition() throws Exception
    {
        final Sequence dependent = new Sequence(16L);
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(dependent);

        final Future<Long> available = executor.submit(() -> sequenceBarrier.waitFor(32L));
        Thread.sleep(50);
        assertFalse(available.isDone());

        dependent.set(32L);

        assertEquals(32L, available.get(2, TimeUnit.SECONDS));
    }

    @Test
    public void shouldInterruptDuringBusySpin() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(3);
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(new CountDownLatchSequence(48L, latch));

        final Future<Long> available = executor.submit(() -> sequenceBarrier.waitFor(64L));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        sequenceBarrier.alert();

        final ExecutionException e = assertThrows(ExecutionException.class, () -> available.get(2, TimeUnit.SECONDS));
        assertInstanceOf(AlertException.class, e.getCause());
    }

    @Test
    public void shouldSetAndClearAlertStatus()
    {
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        assertFalse(sequenceBarrier.isAlerted());

        sequenceBarrier.alert();
        assertTrue(sequenceBarrier.isAlerted());

        sequenceBarrier.clearAlert();
        assertFalse(sequenceBarrier.isAlerted());
    }

    private static final class CountDownLatchSequence extends Sequence
    {
        private final CountDownLatch latch;

        private CountDownLatchSequence(final long initialValue, final CountDownLatch latch)
        {
            super(initialValue);
            this.latch = latch;
        }

        @Override
        public long get()
        {
            latch.countDown();
            return super.get();
        }
    }
}
