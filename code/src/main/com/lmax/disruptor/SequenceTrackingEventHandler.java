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
 * Used by the {@link BatchEventProcessor} to set a callback allowing the {@link BatchEventHandler} to notify
 * when it has finished consuming an {@link AbstractEvent} if this happens after the {@link BatchEventHandler#onAvailable(AbstractEvent)} call.
 * <p>
 * Typically this would be used when the handler is performing some sort of batching operation such are writing to an IO device.
 * </p>
 * @param <T> AbstractEvent implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface SequenceTrackingEventHandler<T extends AbstractEvent>
    extends BatchEventHandler<T>
{
    /**
     * Call by the {@link BatchEventProcessor} to setup the callback.
     *
     * @param sequenceTrackerCallback callback on which to notify the {@link BatchEventProcessor} that the sequence has progressed.
     */
    void setSequenceTrackerCallback(final BatchEventProcessor.SequenceTrackerCallback sequenceTrackerCallback);
}
