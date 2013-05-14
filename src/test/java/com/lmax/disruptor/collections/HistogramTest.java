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
            assertThat(Long.valueOf(histogram.getUpperBoundAt(i)), is(Long.valueOf(INTERVALS[i])));
        }
    }

    @Test
    public void shouldConfirmIntervalsAreInitialised()
    {
        for (int i = 0, size = histogram.getSize(); i < size; i++)
        {
            assertThat(Long.valueOf(histogram.getCountAt(i)), is(Long.valueOf(0L)));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionWhenIntervalLessThanOrEqualToZero()
    {
        new Histogram(new long[]{-1, 10, 20});
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionWhenIntervalDoNotIncrease()
    {
        new Histogram(new long[]{1, 10, 10, 20});
    }

    @Test
    public void shouldAddObservation()
    {
        assertTrue(histogram.addObservation(10L));
        assertThat(Long.valueOf(histogram.getCountAt(1)), is(Long.valueOf(1L)));
    }

    @Test
    public void shouldNotAddObservation()
    {
        Histogram histogram = new Histogram(new long[]{ 10, 20, 30 });
        assertFalse(histogram.addObservation(31));
    }

    @Test
    public void shouldAddObservations()
    {
        addObservations(histogram, 10L, 30L, 50L);

        Histogram histogram2 = new Histogram(INTERVALS);
        addObservations(histogram2, 10L, 20L, 25L);

        histogram.addObservations(histogram2);

        assertThat(Long.valueOf(6L), is(Long.valueOf(histogram.getCount())));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionWhenIntervalsDoNotMatch()
    {
        Histogram histogram2 = new Histogram(new long[]{ 1L, 2L, 3L});
        histogram.addObservations(histogram2);
    }

    @Test
    public void shouldClearCounts()
    {
        addObservations(histogram, 1L, 7L, 10L, 3000L);
        histogram.clear();

        for (int i = 0, size = histogram.getSize(); i < size; i++)
        {
            assertThat(Long.valueOf(histogram.getCountAt(i)), is(Long.valueOf(0)));
        }
    }

    @Test
    public void shouldCountTotalObservations()
    {
        addObservations(histogram, 1L, 7L, 10L, 3000L);

        assertThat(Long.valueOf(histogram.getCount()), is(Long.valueOf(4L)));
    }

    @Test
    public void shouldGetMeanObservation()
    {
        final long[] intervals = new long[]{ 1, 10, 100, 1000, 10000 };
        final Histogram histogram = new Histogram(intervals);

        addObservations(histogram, 1L, 7L, 10L, 10L, 11L, 144L);

        assertThat(histogram.getMean(), is(new BigDecimal("32.67")));
    }

    @Test
    public void shouldCorrectMeanForSkewInTopAndBottomPopulatedIntervals()
    {
        final long[] intervals = new long[]{ 100, 110, 120, 130, 140, 150, 1000, 10000 };
        final Histogram histogram = new Histogram(intervals);

        for (long i = 100; i < 152; i++)
        {
            histogram.addObservation(i);
        }

        assertThat(histogram.getMean(), is(new BigDecimal("125.02")));
    }

    @Test
    public void shouldGetMaxObservation()
    {
        addObservations(histogram, 1L, 7L, 10L, 10L, 11L, 144L);

        assertThat(Long.valueOf(histogram.getMax()), is(Long.valueOf(144L)));
    }

    @Test
    public void shouldGetMinObservation()
    {
        addObservations(histogram, 1L, 7L, 10L, 10L, 11L, 144L);

        assertThat(Long.valueOf(histogram.getMin()), is(Long.valueOf(1L)));
    }

    @Test
    public void shouldGetMinAndMaxOfSingleObservation()
    {
        addObservations(histogram, 10L);

        assertThat(Long.valueOf(histogram.getMin()), is(Long.valueOf(10L)));
        assertThat(Long.valueOf(histogram.getMax()), is(Long.valueOf(10L)));
    }

    @Test
    public void shouldGetTwoNinesUpperBound()
    {
        final long[] intervals = new long[]{ 1, 10, 100, 1000, 10000 };
        final Histogram histogram = new Histogram(intervals);

        for (long i = 1; i < 101; i++)
        {
            histogram.addObservation(i);
        }

        assertThat(Long.valueOf(histogram.getTwoNinesUpperBound()), is(Long.valueOf(100L)));
    }

    @Test
    public void shouldGetFourNinesUpperBound()
    {
        final long[] intervals = new long[]{ 1, 10, 100, 1000, 10000 };
        final Histogram histogram = new Histogram(intervals);

        for (long i = 1; i < 102; i++)
        {
            histogram.addObservation(i);
        }

        assertThat(Long.valueOf(histogram.getFourNinesUpperBound()), is(Long.valueOf(1000L)));
    }

    @Test
    public void shouldToString()
    {
        addObservations(histogram, 1L, 7L, 10L, 300L);

        String expectedResults = "Histogram{min=1, max=300, mean=53.25, 99%=1000, 99.99%=1000, [1=1, 10=2, 100=0, 1000=1, 9223372036854775807=0]}";
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
