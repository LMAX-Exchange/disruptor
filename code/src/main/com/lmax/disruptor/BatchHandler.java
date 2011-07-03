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
 * Callback interface to be implemented for processing {@link AbstractEntry}s as they become available in the {@link RingBuffer}
 *
 * @see BatchConsumer#setExceptionHandler(ExceptionHandler) if you want to handle exceptions propigated out of the handler.
 *
 * @param <T> AbstractEntry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface BatchHandler<T extends AbstractEntry>
{
    /**
     * Called when a publisher has committed an {@link AbstractEntry} to the {@link RingBuffer}
     *
     * @param entry committed to the {@link RingBuffer}
     * @throws Exception if the BatchHandler would like the exception handled further up the chain.
     */
    void onAvailable(T entry) throws Exception;

    /**
     * Called after each batch of items has been have been processed before the next waitFor call on a {@link ConsumerBarrier}.
     * <p>
     * This can be taken as a hint to do flush type operations before waiting once again on the {@link ConsumerBarrier}.
     * The user should not expect any pattern or frequency to the batch size.
     *
     * @throws Exception if the BatchHandler would like the exception handled further up the chain.
     */
    void onEndOfBatch() throws Exception;
}
