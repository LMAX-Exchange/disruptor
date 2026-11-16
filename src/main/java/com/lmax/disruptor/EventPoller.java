package com.lmax.disruptor;

/**
 * Experimental poll-based interface for the Disruptor. Unlike a {@link BatchEventProcessor},
 * an event poller allows the user to control the flow of execution. This makes it ideal
 * for interoperability with existing threads whose lifecycle is not controlled by the
 * disruptor DSL.
 *
 * @param <T> the type of event used.
 */
public class EventPoller<T>
{
    private final DataProvider<T> dataProvider;
    private final Sequencer sequencer;
    private final Sequence sequence;
    private final Sequence gatingSequence;
    private final boolean gatedOnCursor;

    /**
     * A callback used to process events
     *
     * @param <T> the type of the event
     */
    public interface Handler<T>
    {
        /**
         * Called for each event to consume it
         *
         * @param event the event
         * @param sequence the sequence of the event
         * @param endOfBatch whether this event is the last in the batch
         * @return whether to continue consuming events. If {@code false}, the poller will not feed any more events
         *         to the handler until {@link EventPoller#poll(Handler)} is called again
         * @throws Exception any exceptions thrown by the handler will be propagated to the caller of {@code poll}
         */
        boolean onEvent(T event, long sequence, boolean endOfBatch) throws Exception;
    }

    /**
     * Indicates the result of a call to {@link #poll(Handler)}
     */
    public enum PollState
    {
        /**
         * The poller processed one or more events
         */
        PROCESSING,
        /**
         * The poller is waiting for gated sequences to advance before events become available
         */
        GATING,
        /**
         * No events need to be processed
         */
        IDLE
    }

    /**
     * Creates an event poller. Most users will want {@link RingBuffer#newPoller(Sequence...)}
     * which will set up the poller automatically
     *
     * @param dataProvider from which events are drawn
     * @param sequencer the main sequencer which handles ordering of events
     * @param sequence the sequence which will be used by this event poller
     * @param gatingSequence the sequences to gate on
     */
    public EventPoller(
        final DataProvider<T> dataProvider,
        final Sequencer sequencer,
        final Sequence sequence,
        final Sequence gatingSequence)
    {
        this(dataProvider, sequencer, sequence, gatingSequence, false);
    }

    private EventPoller(
        final DataProvider<T> dataProvider,
        final Sequencer sequencer,
        final Sequence sequence,
        final Sequence gatingSequence,
        final boolean gatedOnCursor)
    {
        this.dataProvider = dataProvider;
        this.sequencer = sequencer;
        this.sequence = sequence;
        this.gatingSequence = gatingSequence;
        this.gatedOnCursor = gatedOnCursor;
    }

    /**
     * Polls for events using the given handler. <br>
     * <br>
     * This poller will continue to feed events to the given handler until known available
     * events are consumed or {@link Handler#onEvent(Object, long, boolean)} returns false. <br>
     * <br>
     * Note that it is possible for more events to become available while the current events
     * are being processed. A further call to this method will process such events.
     *
     * @param eventHandler the handler used to consume events
     * @return the state of the event poller after the poll is attempted
     * @throws Exception exceptions thrown from the event handler are propagated to the caller
     */
    public PollState poll(final Handler<T> eventHandler) throws Exception
    {
        final long currentSequence = sequence.get();
        long nextSequence = currentSequence + 1;
        final long availableSequence = sequencer.getHighestPublishedSequence(nextSequence, gatingSequence.get());

        if (nextSequence <= availableSequence)
        {
            boolean processNextEvent;
            long processedSequence = currentSequence;

            try
            {
                do
                {
                    final T event = dataProvider.get(nextSequence);
                    processNextEvent = eventHandler.onEvent(event, nextSequence, nextSequence == availableSequence);
                    processedSequence = nextSequence;
                    nextSequence++;

                }
                while (nextSequence <= availableSequence && processNextEvent);
            }
            finally
            {
                sequence.set(processedSequence);
            }

            return PollState.PROCESSING;
        }
        else if (sequencer.getCursor() >= nextSequence)
        {
            return PollState.GATING;
        }
        else
        {
            return PollState.IDLE;
        }
    }

    /**
     * Polls for events like {@link #poll(Handler)}, but looks only at whether the next events are published.
     * With a multi-producer ring buffer it never reads the cursor that the producers contend on. <br>
     * <br>
     * In exchange it reports less than {@link #poll(Handler)} when there is nothing to process:
     * <ul>
     * <li>{@link PollState#IDLE} means only that the next event is not published. It may already be claimed,
     * where {@link #poll(Handler)} returns {@link PollState#GATING}.</li>
     * <li>{@link PollState#GATING} means that the next event is published and this poller's gating sequences
     * have not passed it. A poller created without gating sequences never returns it.</li>
     * </ul>
     * The {@link #getSequence() sequence} of this poller must be a gating sequence of the ring buffer.
     *
     * @param eventHandler the handler used to consume events
     * @return the state of the event poller after the poll is attempted
     * @throws Exception exceptions thrown from the event handler are propagated to the caller
     */
    public PollState pollRelaxed(final Handler<T> eventHandler) throws Exception
    {
        final long currentSequence = sequence.get();
        long nextSequence = currentSequence + 1;
        if (!sequencer.isAvailable(nextSequence))
        {
            return PollState.IDLE;
        }
        // A poller behind other consumers is bound by their sequences; one behind the producers has no bound.
        final long bound = gatedOnCursor ? Long.MAX_VALUE : gatingSequence.get();
        if (nextSequence > bound)
        {
            return PollState.GATING;
        }

        // Looks one sequence ahead, for endOfBatch. That cannot reach the next lap, as this poller's sequence
        // gates the producers and does not move until the batch is done.
        long processedSequence = currentSequence;
        try
        {
            boolean processNextEvent;
            boolean nextAvailable;
            do
            {
                nextAvailable = nextSequence < bound && sequencer.isAvailable(nextSequence + 1);
                final T event = dataProvider.get(nextSequence);
                processNextEvent = eventHandler.onEvent(event, nextSequence, !nextAvailable);
                processedSequence = nextSequence;
                nextSequence++;
            }
            while (nextAvailable && processNextEvent);
        }
        finally
        {
            sequence.set(processedSequence);
        }

        return PollState.PROCESSING;
    }

    /**
     * Creates an event poller. Most users will want {@link RingBuffer#newPoller(Sequence...)}
     * which will set up the poller automatically
     *
     * @param dataProvider from which events are drawn
     * @param sequencer the main sequencer which handles ordering of events
     * @param sequence the sequence which will be used by this event poller
     * @param cursorSequence the cursor sequence, usually of the ring buffer
     * @param gatingSequences additional sequences to gate on
     * @param <T> the type of the event
     * @return the event poller
     */
    public static <T> EventPoller<T> newInstance(
        final DataProvider<T> dataProvider,
        final Sequencer sequencer,
        final Sequence sequence,
        final Sequence cursorSequence,
        final Sequence... gatingSequences)
    {
        Sequence gatingSequence;
        final boolean gatedOnCursor = gatingSequences.length == 0;
        if (gatedOnCursor)
        {
            gatingSequence = cursorSequence;
        }
        else if (gatingSequences.length == 1)
        {
            gatingSequence = gatingSequences[0];
        }
        else
        {
            gatingSequence = new FixedSequenceGroup(gatingSequences);
        }

        return new EventPoller<>(dataProvider, sequencer, sequence, gatingSequence, gatedOnCursor);
    }

    /**
     * Get the {@link Sequence} being used by this event poller
     *
     * @return the sequence used by the event poller
     */
    public Sequence getSequence()
    {
        return sequence;
    }
}
