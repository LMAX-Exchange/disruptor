package com.lmax.disruptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SequenceTest
{
    @Test
    void shouldReturnChangedValueAfterAddAndGet()
    {
        final Sequence sequence = new Sequence(0);

        assertEquals(10, sequence.addAndGet(10));
        assertEquals(10, sequence.get());
    }

    @Test
    void shouldReturnIncrementedValueAfterIncrementAndGet()
    {
        final Sequence sequence = new Sequence(0);

        assertEquals(1, sequence.incrementAndGet());
        assertEquals(1, sequence.get());
    }

    @Test
    void shouldReturnPreviousValueAfterGetAndAdd()
    {
        final Sequence sequence = new Sequence(0);

        assertEquals(0, sequence.getAndAdd(1));
        assertEquals(1, sequence.get());
    }
}