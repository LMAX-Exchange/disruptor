/*
 * Copyright 2012 LMAX Ltd.
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
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.*;

/**
 * Defines producer types to support creation of RingBuffer with correct sequencer and publisher.
 */
public enum ProducerType implements SequencerFactory
{
    /**
     * Create a RingBuffer with a single event publisher to the RingBuffer
     */
    SINGLE
        {
            @Override
            public Sequencer newInstance(final int bufferSize, final WaitStrategy waitStrategy)
            {
                return new SingleProducerSequencer(bufferSize, waitStrategy);
            }
        },

    /**
     * Create a RingBuffer supporting multiple event publishers to the one RingBuffer
     */
    MULTI
        {
            @Override
            public Sequencer newInstance(final int bufferSize, final WaitStrategy waitStrategy)
            {
                return new MultiProducerSequencer(bufferSize, waitStrategy);
            }
        };

    public static SequencerFactory waitFree(int reserveSize)
    {
        return ((bufferSize, waitStrategy) ->
            new WaitFreeSequencer(bufferSize, waitStrategy, reserveSize));
    }
}
