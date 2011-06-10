package com.lmax.disruptor.collections;

import org.junit.Test;

import java.math.BigDecimal;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public final class HistogramTest
{
    public static final long[] INTERVALS = new long[]{ 1, 10, 100, 1000, Long.MAX_VALUE };
    private Histogram histogram = new Histogram(INTERVALS);

    @Test
    public void shouldSizeBasedOnBucketConfiguration()
    {
        assertThat(Long.valueOf(histogram.getSize()), is(Long.valueOf(INTERVALS.length)));
    }

    @Test
    public void shouldWalkIntervals()
    {
        for (int i = 0, size = histogram.getSize(); i < size; i++)
        {
            assertThat(Long.valueOf(histogram.getIntervalUpperBound(i)), is(Long.valueOf(INTERVALS[i])));
        }
    }

    @Test
    public void shouldConfirmIntervalsAreInitialised()
    {
        for (int i = 0, size = histogram.getSize(); i < size; i++)
        {
            assertThat(Long.valueOf(histogram.getObservationCount(i)), is(Long.valueOf(0L)));
        }
    }

    @Test
    public void shouldAddObservation()
    {
        assertTrue(histogram.addObservation(10L));
        assertThat(Long.valueOf(histogram.getObservationCount(1)), is(Long.valueOf(1L)));
    }

    @Test
    public void shouldNotAddObservation()
    {
        Histogram histogram = new Histogram(new long[]{ 10, 20, 30 });
        assertFalse(histogram.addObservation(31));
    }

    @Test
    public void shouldClearCounts()
    {
        addObservations(histogram, 1L, 7L, 10L, 3000L);
        histogram.clear();

        for (int i = 0, size = histogram.getSize(); i < size; i++)
        {
            assertThat(Long.valueOf(histogram.getObservationCount(i)), is(Long.valueOf(0)));
        }
    }

    @Test
    public void shouldCountTotalObservations()
    {
        addObservations(histogram, 1L, 7L, 10L, 3000L);

        assertThat(Long.valueOf(histogram.countTotalRecordedObservations()), is(Long.valueOf(4L)));
    }

    @Test
    public void shouldGetMeanObservation()
    {
        final long[] INTERVALS = new long[]{ 1, 10, 100, 1000, 10000 };
        final Histogram histogram = new Histogram(INTERVALS);

        addObservations(histogram, 1L, 7L, 10L, 10L, 11L, 144L);

        assertThat(histogram.getMeanObservation(), is(new BigDecimal("94.17")));
    }

    @Test
    public void shouldToString()
    {
        addObservations(histogram, 1L, 7L, 10L, 3000L);

        String expectedResults = "Histogram{1 = 1, 10 = 2, 100 = 0, 1000 = 0, 9223372036854775807 = 1}";
        assertThat(histogram.toString(), is(expectedResults));
    }

    private void addObservations(final Histogram histogram, final long... observations)
    {
        for (int i = 0, size = observations.length; i < size; i++)
        {
            histogram.addObservation(observations[i]);
        }
    }
}
