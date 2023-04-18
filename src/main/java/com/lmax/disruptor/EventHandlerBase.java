/*
 * Copyright 2022 LMAX Ltd.
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

interface EventHandlerBase<T> extends EventHandlerIdentity
{
    /**
     * Called when a publisher has published an event to the {@link RingBuffer}.  The {@link BatchEventProcessor} will
     * read messages from the {@link RingBuffer} in batches, where a batch is all of the events available to be
     * processed without having to wait for any new event to arrive.  This can be useful for event handlers that need
     * to do slower operations like I/O as they can group together the data from multiple events into a single
     * operation.  Implementations should ensure that the operation is always performed when endOfBatch is true as
     * the time between that message and the next one is indeterminate.
     *
     * @param event      published to the {@link RingBuffer}
     * @param sequence   of the event being processed
     * @param endOfBatch flag to indicate if this is the last event in a batch from the {@link RingBuffer}
     * @throws Throwable if the EventHandler would like the exception handled further up the chain or possible rewind
     * the batch if a {@link RewindableException} is thrown.
     */
    void onEvent(T event, long sequence, boolean endOfBatch) throws Throwable;

    /**
     * Invoked by {@link BatchEventProcessor} prior to processing a batch of events
     *
     * @param batchSize the size of the batch that is starting
     * @param queueDepth the total number of queued up events including the batch about to be processed
     */
    default void onBatchStart(long batchSize, long queueDepth)
    {
    }

    /**
     * Called once on thread start before first event is available.
     */
    default void onStart()
    {
    }

    /**
     * Called once just before the event processing thread is shutdown.
     *
     * <p>Sequence event processing will already have stopped before this method is called. No events will
     * be processed after this message.
     */
    default void onShutdown()
    {
    }

    /**
     * Invoked when a {@link BatchEventProcessor}'s {@link WaitStrategy} throws a {@link TimeoutException}.
     *
     * @param sequence - the last processed sequence.
     * @throws Exception if the implementation is unable to handle this timeout.
     */
    default void onTimeout(long sequence) throws Exception
    {
    }
}
