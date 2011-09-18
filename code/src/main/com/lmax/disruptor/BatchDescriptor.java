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
 * Used to record the batch of sequences claimed via a {@link Sequencer}.
 */
public final class BatchDescriptor
{
    private final int size;
    private long end = Sequencer.INITIAL_CURSOR_VALUE;

    /**
     * Create a holder for tracking a batch of claimed sequences in a {@link Sequencer}
     * @param size of the batch to claim.
     */
    BatchDescriptor(final int size)
    {
        this.size = size;
    }

    /**
     * Get the end sequence of a batch.
     *
     * @return the end sequence in a batch
     */
    public long getEnd()
    {
        return end;
    }

    /**
     * Set the end of the batch sequence.  To be used by the {@link Sequencer}.
     *
     * @param end sequence in the batch.
     */
    void setEnd(final long end)
    {
        this.end = end;
    }

    /**
     * Get the size of the batch.
     *
     * @return the size of the batch.
     */
    public int getSize()
    {
        return size;
    }

    /**
     * Get the starting sequence for a batch.
     *
     * @return the starting sequence of a batch.
     */
    public long getStart()
    {
        return end - (size - 1L);
    }
}
