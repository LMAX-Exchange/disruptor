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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * <p>Histogram for tracking the frequency of observations of values below interval upper bounds.</p>
 *
 * <p>This class is useful for recording timings across a large number of observations
 * when high performance is required.<p>
 *
 * <p>The interval bounds are used to define the ranges of the histogram buckets. If provided bounds
 * are [10,20,30,40,50] then there will be five buckets, accessible by index 0-4. Any value
 * 0-10 will fall into the first interval bar, values 11-20 will fall into the
 * second bar, and so on.</p>
 */
public final class Histogram
{
    // tracks the upper intervals of each of the buckets/bars
    private final long[] upperBounds;
    // tracks the count of the corresponding bucket
    private final long[] counts;
    // minimum value so far observed
    private long minValue = Long.MAX_VALUE;
    // maximum value so far observed
    private long maxValue = 0L;

    /**
     * Create a new Histogram with a provided list of interval bounds.
     *
     * @param upperBounds of the intervals. Bounds must be provided in order least to greatest, and
     * lowest bound must be greater than or equal to 1.
     * @throws IllegalArgumentException if any of the upper bounds are less than or equal to zero
     * @throws IllegalArgumentException if the bounds are not in order, least to greatest
     */
    public Histogram(final long[] upperBounds)
    {
        validateBounds(upperBounds);

        this.upperBounds = Arrays.copyOf(upperBounds, upperBounds.length);
        this.counts = new long[upperBounds.length];
    }

    /**
     * Validates the input bounds; used by constructor only.
     */
    private void validateBounds(final long[] upperBounds)
    {
        long lastBound = -1L;
        if (upperBounds.length <= 0)
        {
            throw new IllegalArgumentException("Must provide at least one interval");
        }
        for (final long bound : upperBounds)
        {
            if (bound <= 0L)
            {
                throw new IllegalArgumentException("Bounds must be positive values");
            }

            if (bound <= lastBound)
            {
                throw new IllegalArgumentException("bound " + bound + " is not greater than " + lastBound);
            }

            lastBound = bound;
        }
    }

    /**
     * Size of the list of interval bars (ie: count of interval bars).
     *
     * @return size of the interval bar list.
     */
    public int getSize()
    {
        return upperBounds.length;
    }

    /**
     * Get the upper bound of an interval for an index.
     *
     * @param index of the upper bound.
     * @return the interval upper bound for the index.
     */
    public long getUpperBoundAt(final int index)
    {
        return upperBounds[index];
    }

    /**
     * Get the count of observations at a given index.
     *
     * @param index of the observations counter.
     * @return the count of observations at a given index.
     */
    public long getCountAt(final int index)
    {
        return counts[index];
    }

    /**
     * Add an observation to the histogram and increment the counter for the interval it matches.
     *
     * @param value for the observation to be added.
     * @return return true if in the range of intervals and successfully added observation; otherwise false.
     */
    public boolean addObservation(final long value)
    {
        int low = 0;
        int high = upperBounds.length - 1;

        // do a classic binary search to find the high value
        while (low < high)
        {
            int mid = low + ((high - low) >> 1);
            if (upperBounds[mid] < value)
            {
                low = mid + 1;
            }
            else
            {
                high = mid;
            }
        }

        // if the binary search found an eligible bucket, increment
        if (value <= upperBounds[high])
        {
            counts[high]++;
            trackRange(value);

            return true;
        }

        // otherwise value was not found
        return false;
    }

    /**
     * Track minimum and maximum observations
     *
     * @see getMin
     * @see getMax
     */
    private void trackRange(final long value)
    {
        if (value < minValue)
        {
            minValue = value;
        }

        if (value > maxValue)
        {
            maxValue = value;
        }
    }

    /**
     * <p>Add observations from another Histogram into this one.</p>
     *
     * <p>Histograms must have the same intervals.</p>
     *
     * @param histogram from which to add the observation counts.
     * @throws IllegalArgumentException if interval count or values do not match exactly
     */
    public void addObservations(final Histogram histogram)
    {
        // validate the intervals
        if (upperBounds.length != histogram.upperBounds.length)
        {
            throw new IllegalArgumentException("Histograms must have matching intervals");
        }

        for (int i = 0, size = upperBounds.length; i < size; i++)
        {
            if (upperBounds[i] != histogram.upperBounds[i])
            {
                throw new IllegalArgumentException("Histograms must have matching intervals");
            }
        }

        // increment all of the internal counts
        for (int i = 0, size = counts.length; i < size; i++)
        {
            counts[i] += histogram.counts[i];
        }

        // refresh the minimum and maximum observation ranges
        trackRange(histogram.minValue);
        trackRange(histogram.maxValue);
    }

    /**
     * Clear the list of interval counters
     */
    public void clear()
    {
        maxValue = 0L;
        minValue = Long.MAX_VALUE;

        for (int i = 0, size = counts.length; i < size; i++)
        {
            counts[i] = 0L;
        }
    }

    /**
     * Count total number of recorded observations.
     *
     * @return the total number of recorded observations.
     */
    public long getCount()
    {
        long count = 0L;

        for (int i = 0, size = counts.length; i < size; i++)
        {
            count += counts[i];
        }

        return count;
    }

    /**
     * Get the minimum observed value.
     *
     * @return the minimum value observed.
     */
    public long getMin()
    {
        return minValue;
    }

    /**
     * Get the maximum observed value.
     *
     * @return the maximum of the observed values;
     */
    public long getMax()
    {
        return maxValue;
    }

    /**
     * <p>Calculate the mean of all recorded observations.</p>
     *
     * <p>The mean is calculated by summing the mid points of each interval multiplied by the count
     * for that interval, then dividing by the total count of observations.  The max and min are
     * considered for adjusting the top and bottom bin when calculating the mid point, this
     * minimises skew if the observed values are very far away from the possible histogram values.</p>
     *
     * @return the mean of all recorded observations.
     */
    public BigDecimal getMean()
    {
        // early exit to avoid divide by zero later
        if (0L == getCount())
        {
            return BigDecimal.ZERO;
        }

        // precalculate the initial lower bound; needed in the loop
        long lowerBound = counts[0] > 0L ? minValue : 0L;
        // use BigDecimal to avoid precision errors
        BigDecimal total = BigDecimal.ZERO;

        // midpoint is calculated as the average between the lower and upper bound
        // (after taking into account the min & max values seen)
        // then, simply multiply midpoint by the count of values at the interval (intervalTotal)
        // and add to running total (total)
        for (int i = 0, size = upperBounds.length; i < size; i++)
        {
            if (0L != counts[i])
            {
                long upperBound = Math.min(upperBounds[i], maxValue);
                long midPoint = lowerBound + ((upperBound - lowerBound) / 2L);

                BigDecimal intervalTotal = new BigDecimal(midPoint).multiply(new BigDecimal(counts[i]));
                total = total.add(intervalTotal);
            }

            // and recalculate the lower bound for the next time around the loop
            lowerBound = Math.max(upperBounds[i] + 1L, minValue);
        }

        return total.divide(new BigDecimal(getCount()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the upper bound within which 99% of observations fall.
     *
     * @return the upper bound for 99% of observations.
     */
    public long getTwoNinesUpperBound()
    {
        return getUpperBoundForFactor(0.99d);
    }

    /**
     * Calculate the upper bound within which 99.99% of observations fall.
     *
     * @return the upper bound for 99.99% of observations.
     */
    public long getFourNinesUpperBound()
    {
        return getUpperBoundForFactor(0.9999d);
    }

    /**
     * <p>Get the interval upper bound for a given factor of the observation population.</p>
     *
     * <p>Note this does not get the actual percentile measurement, it only gets the bucket</p>
     *
     * @param factor representing the size of the population.
     * @return the interval upper bound.
     * @throws IllegalArgumentException if factor &lt; 0.0 or factor &gt; 1.0
     */
    public long getUpperBoundForFactor(final double factor)
    {
        if (0.0d >= factor || factor >= 1.0d)
        {
            throw new IllegalArgumentException("factor must be >= 0.0 and <= 1.0");
        }

        final long totalCount = getCount();
        final long tailTotal = totalCount - Math.round(totalCount * factor);
        long tailCount = 0L;

        // reverse search the intervals ('tailCount' from end)
        for (int i = counts.length - 1; i >= 0; i--)
        {
            if (0L != counts[i])
            {
                tailCount += counts[i];
                if (tailCount >= tailTotal)
                {
                    return upperBounds[i];
                }
            }
        }

        return 0L;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Histogram{");

        sb.append("min=").append(getMin()).append(", ");
        sb.append("max=").append(getMax()).append(", ");
        sb.append("mean=").append(getMean()).append(", ");
        sb.append("99%=").append(getTwoNinesUpperBound()).append(", ");
        sb.append("99.99%=").append(getFourNinesUpperBound()).append(", ");

        sb.append('[');
        for (int i = 0, size = counts.length; i < size; i++)
        {
            sb.append(upperBounds[i]).append('=').append(counts[i]).append(", ");
        }

        if (counts.length > 0)
        {
            sb.setLength(sb.length() - 2);
        }
        sb.append(']');

        sb.append('}');

        return sb.toString();
    }
}
