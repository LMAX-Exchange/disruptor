package com.lmax.disruptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class SingleProducerSequencerTest
{
    @Test
    public void shouldNotUpdateCursorDuringHasAvailableCapacity() throws Exception
    {
        SingleProducerSequencer sequencer = new SingleProducerSequencer(16, new BusySpinWaitStrategy());

        for (int i = 0; i < 32; i++)
        {
            long next = sequencer.next();
            assertNotEquals(sequencer.cursor.get(), next);

            sequencer.hasAvailableCapacity(13);
            assertNotEquals(sequencer.cursor.get(), next);

            sequencer.publish(next);
        }
    }
}