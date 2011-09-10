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
 * No operation version of a {@link EventProcessor} that simply tracks a {@link Sequencer}.
 * This is useful in tests or for pre-filling a {@link RingBuffer} from a publisher.
 */
public final class NoOpEventProcessor implements EventProcessor
{
    private final SequencerFollowingSequence sequence;

    /**
     * Construct a {@link EventProcessor} that simply tracks a {@link Sequencer}.
     *
     * @param sequencer to track.
     */
    public NoOpEventProcessor(final Sequencer sequencer)
    {
        sequence = new SequencerFollowingSequence(sequencer);
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
    }

    @Override
    public void run()
    {
    }

    private static final class SequencerFollowingSequence extends Sequence
    {
        private final Sequencer sequencer;

        private SequencerFollowingSequence(final Sequencer sequencer)
        {
            super(Sequencer.INITIAL_CURSOR_VALUE);
            this.sequencer = sequencer;
        }

        @Override
        public long get()
        {
            return sequencer.getCursor();
        }
    }
}
