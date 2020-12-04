package com.lmax.disruptor;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

public class SingleProducerSequencerTest
{
    @Test
    public void shouldNotUpdateCursorDuringHasAvailableCapacity() throws Exception
    {
        SingleProducerSequencer sequencer = new SingleProducerSequencer(16, new BusySpinWaitStrategy());

        for (int i = 0; i < 32; i++)
        {
            long next = sequencer.next();
            assertThat(sequencer.cursor.get(), not(next));

            sequencer.hasAvailableCapacity(13);
            assertThat(sequencer.cursor.get(), not(next));

            sequencer.publish(next);
        }
    }
}