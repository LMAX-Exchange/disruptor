package com.lmax.disruptor;

public class EventPoller<T>
{
    private final DataProvider<T> dataProvider;
    private final Sequencer sequencer;
    private final Sequence sequence;
    private final Sequence gatingSequence;

    public interface Handler<T>
    {
        boolean onEvent(T event, long sequence, boolean endOfBatch);
    }

    public enum PollState
    {
        PROCESSING, GATING, IDLE
    }

    public EventPoller(DataProvider<T> dataProvider,
                       Sequencer sequencer,
                       Sequence sequence,
                       Sequence gatingSequence)
    {
        this.dataProvider = dataProvider;
        this.sequencer = sequencer;
        this.sequence = sequence;
        this.gatingSequence = gatingSequence;
    }

    public PollState poll(Handler<T> eventHandler)
    {
        long currentSequence = sequence.get();
        long nextSequence = currentSequence + 1;
        long availableSequence = sequencer.getHighestPublishedSequence(sequence.get(), gatingSequence.get());

        if (gatingSequence.get() >= nextSequence)
        {
            long eventSequence = nextSequence;
            while (eventSequence <= availableSequence)
            {
                T event = dataProvider.get(eventSequence);
                boolean processNextEvent = eventHandler.onEvent(event, eventSequence, eventSequence == availableSequence);
                eventSequence++;

                if (!processNextEvent)
                {
                    break;
                }
            }

            sequence.set(eventSequence);

            return PollState.PROCESSING;
        }
        else if (sequencer.getHighestPublishedSequence(sequence.get(), sequencer.getCursor()) >= nextSequence)
        {
            return PollState.GATING;
        }
        else
        {
            return PollState.IDLE;
        }
    }

    public static <T> EventPoller<T> newInstance(DataProvider<T> dataProvider,
                                                 Sequencer sequencer,
                                                 Sequence sequence,
                                                 Sequence cursorSequence,
                                                 Sequence...gatingSequences)
    {
        Sequence gatingSequence;
        if (gatingSequences.length == 0)
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

        return new EventPoller<T>(dataProvider, sequencer, sequence, gatingSequence);
    }
}
