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
 * Callback interface to be implemented for processing {@link AbstractEvent}s as they become available in the {@link RingBuffer}
 *
 * @see BatchEventProcessor#setExceptionHandler(ExceptionHandler) if you want to handle exceptions propigated out of the handler.
 *
 * @param <T> AbstractEvent implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface BatchEventHandler<T extends AbstractEvent>
{
    /**
     * Called when a publisher has published an {@link AbstractEvent} to the {@link RingBuffer}
     *
     * @param event published to the {@link RingBuffer}
     * @throws Exception if the BatchEventHandler would like the exception handled further up the chain.
     */
    void onAvailable(T event) throws Exception;

    /**
     * Called after each batch of events has been have been processed before the next waitFor call on a {@link EventProcessorBarrier}.
     * <p>
     * This can be taken as a hint to do flush type operations before waiting once again on the {@link EventProcessorBarrier}.
     * The user should not expect any pattern or frequency to the batch size.
     *
     * @throws Exception if the BatchEventHandler would like the exception handled further up the chain.
     */
    void onEndOfBatch() throws Exception;
}
