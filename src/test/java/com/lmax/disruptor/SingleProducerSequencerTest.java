package com.lmax.disruptor;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    public void shouldNotPublishOutstandingClaimDuringNext()
    {
        final SingleProducerSequencer sequencer = new SingleProducerSequencer(2, new BusySpinWaitStrategy());
        sequencer.publish(sequencer.next());
        sequencer.next();

        // 1 is claimed but not published, and claim 2 reuses the slot of 0.
        sequencer.next();

        assertThat(sequencer.cursor.get(), is(0L));
    }

    @Test
    public void shouldNotPublishOutstandingClaimDuringTryNext() throws Exception
    {
        final SingleProducerSequencer sequencer = new SingleProducerSequencer(2, new BusySpinWaitStrategy());
        sequencer.publish(sequencer.next());
        sequencer.next();

        // 1 is claimed but not published, and claim 2 reuses the slot of 0.
        sequencer.tryNext();

        assertThat(sequencer.cursor.get(), is(0L));
    }

    @Test
    public void shouldNotClaimMoreThanBufferSizeWithoutPublishing()
    {
        final SingleProducerSequencer sequencer = new SingleProducerSequencer(2, new BusySpinWaitStrategy());
        sequencer.next();
        sequencer.next();

        // 0 and 1 are claimed but not published, and claim 2 would reuse the slot of 0.
        assertThrows(InsufficientCapacityException.class, sequencer::tryNext);
    }
}
