package com.lmax.disruptor;

/**
 * Set of common functions used by the Disruptor
 */
public final class Util
{
    /**
     * Calculate the next power of 2, greater than or equal to x.<p>
     * From Hacker's Delight, Chapter 3, Harry S. Warren Jr.
     *
     * @param x Value to round up
     * @return The next power of 2 from x inclusive
     */
    public static int ceilingNextPowerOfTwo(final int x)
    {
        return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
    }

    /**
     * Get the minimum sequence from an array of {@link EntryConsumer}s.
     *
     * @param entryConsumers to compare.
     * @return the minimum sequence found or Long.MAX_VALUE if the array is empty.
     */
    public static long getMinimumSequence(final EntryConsumer[] entryConsumers)
    {
        long minimum = Long.MAX_VALUE;

        for (EntryConsumer consumer : entryConsumers)
        {
            long sequence = consumer.getSequence();
            minimum = minimum < sequence ? minimum : sequence;
        }

        return minimum;
    }
}
