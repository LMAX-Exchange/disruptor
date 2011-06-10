package com.lmax.disruptor.collections;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * Class for tracking observations of values below interval upper bounds.
 */
public final class Histogram
{
    private final long[] intervalUpperBounds;
    private final long[] counts;

    /**
     * Create a new Histogram with a provided list of interval bounds.
     *
     * @param intervalUpperBounds upper bounds of the intervals.
     */
    public Histogram(final long[] intervalUpperBounds)
    {
        this.intervalUpperBounds = Arrays.copyOf(intervalUpperBounds, intervalUpperBounds.length);
        this.counts = new long[intervalUpperBounds.length];
        validateBounds();
    }

    private void validateBounds()
    {
        long lastBound = -1;
        for (final long bound : intervalUpperBounds)
        {
            if (bound <= 0)
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
     * Size of the list of interval bars.
     *
     * @return size of the interval bar list.
     */
    public int getSize()
    {
        return intervalUpperBounds.length;
    }

    /**
     * Get the upper bound of an interval for an index.
     *
     * @param index of the upper bound.
     * @return the interval upper bound for the index.
     */
    public long getIntervalUpperBound(final int index)
    {
        return intervalUpperBounds[index];
    }

    /**
     * Get the count of observations at a given index.
     *
     * @param index of the observations counter.
     * @return the count of observations at a given index.
     */
    public long getObservationCount(final int index)
    {
        return counts[index];
    }

    /**
     * Add an observation to the histogram and increment the counter for the interval it matches.
     *
     * @param value for the observation to be added.
     * @return return true if in the range of intervals otherwise false.
     */
    public boolean addObservation(final long value)
    {
        int low = 0;
        int high = intervalUpperBounds.length - 1;

        while (low < high)
        {
            int mid = low + ((high - low) >> 1);
            if (intervalUpperBounds[mid] < value)
            {
                low = mid + 1;
            }
            else
            {
                high = mid;
            }
        }

        if (value <= intervalUpperBounds[high])
        {
            counts[high]++;
            return true;
        }

        return false;
    }

    /**
     * Clear the list of interval counters.
     */
    public void clear()
    {
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
    public long countTotalRecordedObservations()
    {
        long count = 0L;

        for (int i = 0, size = counts.length; i < size; i++)
        {
            count += counts[i];
        }

        return count;
    }

    /**
     * Calculate the mean of all recorded observations.
     *
     * @return the mean of all recorded observations.
     */
    public BigDecimal getMeanObservation()
    {
        long lowerBound = 0L;
        BigDecimal total = BigDecimal.ZERO;


        for (int i = 0, size = intervalUpperBounds.length; i < size; i++)
        {
            long upperBound = intervalUpperBounds[i];
            long midPoint = lowerBound + ((upperBound - lowerBound) / 2);

            BigDecimal intervalTotal = new BigDecimal(midPoint).multiply(new BigDecimal(counts[i]));
            total = total.add(intervalTotal);
        }

        return total.divide(new BigDecimal(countTotalRecordedObservations()), 2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Histogram{");

        for (int i = 0, size = counts.length; i < size; i++)
        {
            sb.append(intervalUpperBounds[i]).append(" = ").append(counts[i]).append(", ");
        }

        if (counts.length > 0)
        {
            sb.setLength(sb.length() - 2);
        }

        sb.append('}');

        return sb.toString();
    }
}