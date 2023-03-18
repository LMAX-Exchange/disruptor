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

import com.lmax.disruptor.support.StubEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class MaxBatchSizeEventProcessorTest
{
    public static final int MAX_BATCH_SIZE = 3;
    public static final int PUBLISH_COUNT = 5;
    private final RingBuffer<StubEvent> ringBuffer = createSingleProducer(StubEvent.EVENT_FACTORY, 16);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private CountDownLatch countDownLatch;
    private BatchEventProcessor<StubEvent> batchEventProcessor;
    private Thread thread;
    private BatchLimitRecordingHandler eventHandler;

    @BeforeEach
    void setUp()
    {
        countDownLatch = new CountDownLatch(PUBLISH_COUNT);
        eventHandler = new BatchLimitRecordingHandler(countDownLatch);

        batchEventProcessor = new BatchEventProcessorBuilder()
                .setMaxBatchSize(MAX_BATCH_SIZE)
                .build(ringBuffer, this.sequenceBarrier, eventHandler);

        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());

        thread = new Thread(batchEventProcessor);
        thread.start();
    }

    @Test
    public void shouldLimitTheBatchToConfiguredMaxBatchSize() throws Exception
    {
        publishEvents();

        assertEquals(eventHandler.batchedSequences, Arrays.asList(Arrays.asList(0L, 1L, 2L), Arrays.asList(3L, 4L)));
    }

    @Test
    public void shouldAnnounceBatchSizeAndQueueDepthAtTheStartOfBatch() throws Exception
    {
        publishEvents();

        assertEquals(eventHandler.announcedBatchSizes, Arrays.asList(3L, 2L));
        assertEquals(eventHandler.announcedQueueDepths, Arrays.asList(5L, 2L));
    }

    @AfterEach
    void tearDown() throws InterruptedException
    {
        batchEventProcessor.halt();
        thread.join();
    }

    private void publishEvents() throws InterruptedException
    {
        long sequence = 0;
        for (int i = 0; i < PUBLISH_COUNT; i++)
        {
            sequence = ringBuffer.next();
        }
        ringBuffer.publish(sequence);

        //Wait for consumer to process all events
        countDownLatch.await();
    }

    private static class BatchLimitRecordingHandler implements EventHandler<StubEvent>
    {
        public final List<List<Long>> batchedSequences = new ArrayList<>();
        private List<Long> currentSequences;
        private final CountDownLatch countDownLatch;
        private final List<Long> announcedBatchSizes = new ArrayList<>();
        private final List<Long> announcedQueueDepths = new ArrayList<>();

        BatchLimitRecordingHandler(final CountDownLatch countDownLatch)
        {
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void onEvent(final StubEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            currentSequences.add(sequence);
            if (endOfBatch)
            {
                batchedSequences.add(currentSequences);
                currentSequences = null;
            }

            countDownLatch.countDown();
        }

        @Override
        public void onBatchStart(final long batchSize, final long queueDepth)
        {
            currentSequences = new ArrayList<>();
            announcedBatchSizes.add(batchSize);
            announcedQueueDepths.add(queueDepth);
        }
    }
}
