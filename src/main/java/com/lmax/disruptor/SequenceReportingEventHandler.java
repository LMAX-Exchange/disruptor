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
 * Used by the {@link BatchEventProcessor} to set a callback allowing the {@link EventHandler} to notify
 * when it has finished consuming an event if this happens after the {@link EventHandler#onEvent(Object, long, boolean)} call.
 * <p>
 * Typically this would be used when the handler is performing some sort of batching operation such as writing to an IO
 * device; after the operation has completed, the implementation should call {@link Sequence#set} to update the
 * sequence and allow other processes that are dependent on this handler to progress.
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface SequenceReportingEventHandler<T>
    extends EventHandler<T>
{
    /**
     * Call by the {@link BatchEventProcessor} to setup the callback.
     *
     * @param sequenceCallback callback on which to notify the {@link BatchEventProcessor} that the sequence has progressed.
     */
    void setSequenceCallback(Sequence sequenceCallback);
}
