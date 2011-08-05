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

/**
 * Abstraction for claiming {@link AbstractEvent}s in a {@link RingBuffer} while tracking dependent {@link EventProcessor}s
 *
 * @param <T> {@link AbstractEvent} implementation stored in the {@link RingBuffer}
 */
public interface Publisher<T extends AbstractEvent>
{
    /**
     * Get the {@link AbstractEvent} for a given sequence from the underlying {@link RingBuffer}.
     *
     * @param sequence of the {@link AbstractEvent} to get.
     * @return the {@link AbstractEvent} for the sequence.
     */
    T getEvent(long sequence);

    /**
     * Delegate a call to the {@link RingBuffer#getCursor()}
     *
     * @return value of the cursor for entries that have been published.
     */
    long getCursor();

    /**
     * Claim the next {@link AbstractEvent} in sequence for a publisher on the {@link RingBuffer}
     *
     * @return the claimed {@link AbstractEvent}
     */
    T nextEvent();

    /**
     * Claim the next batch of {@link AbstractEvent}s in sequence.
     *
     * @param sequenceBatch to be updated for the batch range.
     * @return the updated sequenceBatch.
     */
    SequenceBatch nextEvents(SequenceBatch sequenceBatch);

    /**
     * Publish an event back to the {@link RingBuffer} to make it visible to {@link EventProcessor}s
     * @param event to be published from the {@link RingBuffer}
     */
    void publish(T event);

    /**
     * Publish the batch of events from to the {@link RingBuffer}.
     *
     * @param sequenceBatch to be published.
     */
    void publish(SequenceBatch sequenceBatch);
}
