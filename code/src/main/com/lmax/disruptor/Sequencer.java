package com.lmax.disruptor;

public interface Sequencer
{
    /** Set to -1 as sequence starting point */
    public static final long INITIAL_CURSOR_VALUE = -1L;

    /**
     * Set the sequences that will gate publishers to prevent the buffer wrapping.
     *
     * This method must be called prior to claiming sequences otherwise
     * a NullPointerException will be thrown.
     *
     * @param sequences to be to be gated on.
     */
    void setGatingSequences(final Sequence... sequences);

    /**
     * Create a {@link SequenceBarrier} that gates on the the cursor and a list of {@link Sequence}s
     *
     * @param sequencesToTrack this barrier will track
     * @return the barrier gated as required
     */
    SequenceBarrier newBarrier(final Sequence... sequencesToTrack);

    /**
     * The capacity of the data structure to hold entries.
     *
     * @return the size of the RingBuffer.
     */
    int getBufferSize();

    /**
     * Get the value of the cursor indicating the published sequence.
     *
     * @return value of the cursor for events that have been published.
     */
    long getCursor();

    /**
     * Has the buffer got capacity to allocate another sequence.  This is a concurrent
     * method so the response should only be taken as an indication of available capacity.
     *
     * @param requiredCapacity in the buffer
     * @return true if the buffer has the capacity to allocate the next sequence otherwise false.
     */
    boolean hasAvailableCapacity(final int requiredCapacity);

    /**
     * Claim the next event in sequence for publishing.
     *
     * @return the claimed sequence value
     */
    long next();

    /**
     * Attempt to claim the next event in sequence for publishing.  Will return the
     * number of the slot if there is at least <code>requiredCapacity</code> slots
     * available.  
     * 
     * @param requiredCapacity
     * @return the claimed sequence value
     * @throws InsufficientCapacityException
     */
    long tryNext(int requiredCapacity) throws InsufficientCapacityException;

    /**
     * Claim a specific sequence when only one publisher is involved.
     *
     * @param sequence to be claimed.
     * @return sequence just claimed.
     */
    long claim(final long sequence);

    /**
     * Publish an event and make it visible to {@link EventProcessor}s
     *
     * @param sequence to be published
     */
    void publish(final long sequence);

    /**
     * Force the publication of a cursor sequence.
     *
     * Only use this method when forcing a sequence and you are sure only one publisher exists.
     * This will cause the cursor to advance to this sequence.
     *
     * @param sequence which is to be forced for publication.
     */
    void forcePublish(final long sequence);

    /**
     * Get the remaining capacity for this sequencer.
     * 
     * @return The number of slots remaining.
     */
    long remainingCapacity();

    /**
     * Spin until the entry pointed to by this sequence is available to be read.
     * 
     * @param sequence
     */
    void ensureAvailable(long sequence);

    /**
     * Determine if the entry referenced by sequence is available
     * 
     * @param sequence
     * @return true if the entry is available 
     */
    boolean isAvailable(long sequence);

}