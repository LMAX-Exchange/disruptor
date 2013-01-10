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
package com.lmax.disruptor;

/**
 * A Publisher optimised for publishing events from a single thread.<p/>
 *
 * Generally not safe for use from multiple threads as it does not implement any barriers.
 */
class SingleProducerPublisher implements Publisher
{
    private final WaitStrategy waitStrategy;
    private final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    public SingleProducerPublisher(WaitStrategy waitStrategy)
    {
        this.waitStrategy = waitStrategy;
    }

    @Override
    public void publish(long sequence)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void ensureAvailable(long sequence)
    {
    }

    @Override
    public boolean isAvailable(long sequence)
    {
        return sequence <= cursor.get();
    }

    Sequence getCursorSequence()
    {
        return cursor;
    }
}
