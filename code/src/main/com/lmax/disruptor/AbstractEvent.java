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
 * Base implementation that must be extended for {@link RingBuffer} events.
 */
public abstract class AbstractEvent
{
    private long sequence;

    /**
     * Get the sequence number assigned to this event in series.
     *
     * @return the sequence number
     */
    public final long getSequence()
    {
        return sequence;
    }

    /**
     * Explicitly set the sequence number for this event.
     *
     * @param sequence to be assigned to this Event
     */
    final void setSequence(final long sequence)
    {
        this.sequence = sequence;
    }
}
