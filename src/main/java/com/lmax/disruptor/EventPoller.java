package com.lmax.disruptor;

/**
 * Experimental poll-based interface for the Disruptor. Unlike a {@link BatchEventProcessor},
 * an event poller allows the user to control the flow of execution. This makes it ideal
 * for interoperability with existing threads whose lifecycle is not controlled by the
 * disruptor DSL.
 *
 * <p>Disruptor 的基于轮询的实验性接口。
 * 与{@link BatchEventProcessor}不同，事件轮询器允许用户控制执行流程。
 * 这使其非常适合与不受 disruptor DSL 控制的现有线程进行交互。</p>
 *
 * @param <T> the type of event used.
 */
public class EventPoller<T>
{
    // 数据提供者，一般指 RingBuffer
    private final DataProvider<T> dataProvider;
    private final Sequencer sequencer;
    // 这是一个新建的 sequence，-1（不是 cursor）
    // Sequencer 调用的时候传的是 new Sequence()
    // 它代表这个 poller 的消费进度
    private final Sequence sequence;
    // 消费者对应的 gating sequence；实际是一个 sequenceGroup
    private final Sequence gatingSequence;

    /**
     * A callback used to process events
     *
     * <p>用于处理事件的回调，类似 EventHandler</p>
     *
     * @param <T> the type of the event
     */
    public interface Handler<T>
    {
        /**
         * Called for each event to consume it
         *
         * <p>调用每个事件以消费它</p>
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
     *
     * <p>指示对{@link #poll(Handler)}的调用的结果</p>
     */
    public enum PollState
    {
        /**
         * The poller processed one or more events
         *
         * <p>轮询器处理了一个或多个事件</p>
         */
        PROCESSING,
        /**
         * The poller is waiting for gated sequences to advance before events become available
         *
         * <p>轮询器正在等待 gated sequences 提前，以便事件变得可用</p>
         */
        GATING,
        /**
         * No events need to be processed
         *
         * <p>不需要处理事件</p>
         */
        IDLE
    }

    /**
     * Creates an event poller. Most users will want {@link RingBuffer#newPoller(Sequence...)}
     * which will set up the poller automatically
     *
     * <p>创建一个事件轮询器。大多数用户将希望使用{@link RingBuffer#newPoller(Sequence...)}，它将自动设置轮询器</p>
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
        this.dataProvider = dataProvider;
        this.sequencer = sequencer;
        this.sequence = sequence;
        this.gatingSequence = gatingSequence;
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
     * <p>使用给定的处理程序轮询事件。<br>
     * <br>
     * 此轮询器将继续向给定处理程序提供事件，直到已知可用事件被消耗或{@link Handler#onEvent(Object, long, boolean)}返回false。<br>
     * <br>
     * 请注意，当当前事件正在处理时，可能会有更多事件变得可用。再次调用此方法将处理这些事件。</p>
     *
     * @param eventHandler the handler used to consume events
     * @return the state of the event poller after the poll is attempted
     * @throws Exception exceptions thrown from the event handler are propagated to the caller
     */
    public PollState poll(final Handler<T> eventHandler) throws Exception
    {
        // 取到当前 poller 的 sequence，即消费进度
        final long currentSequence = sequence.get();
        // 计算下一个需要消费的 sequence
        long nextSequence = currentSequence + 1;
        // 从 Sequencer 处获取最新的已发布的 sequence；
        // 理论上二者之间的差值就是当前 poller 可以消费的所有 event
        // 由于后续计算依赖 availableSequence，而计算期间 highestPublishedSequence 会变化，因此实际 availableSequence 会偏小
        // 注意入参，gateSequence.get()，它会返回一个 group 中最小的 Sequence
        final long availableSequence = sequencer.getHighestPublishedSequence(nextSequence, gatingSequence.get());

        if (nextSequence <= availableSequence)
        {
            boolean processNextEvent;
            long processedSequence = currentSequence;

            try
            {
                // 持续消费
                do
                {
                    // 从 ringBuffer 处获取 event
                    final T event = dataProvider.get(nextSequence);
                    // 消费 event，并记录消费结果
                    processNextEvent = eventHandler.onEvent(event, nextSequence, nextSequence == availableSequence);
                    processedSequence = nextSequence;
                    nextSequence++;
                }
                while (nextSequence <= availableSequence && processNextEvent);
            }
            finally
            {
                // 更新 poller 的消费进度
                sequence.set(processedSequence);
            }

            // 返回 PROCESSING，表示消费了一个或多个 event
            return PollState.PROCESSING;
        }
        // 如果消息 publish 的 sequence >= nextSequence，说明有消息发布了，但是 availableSequence 还没有变化
        // 说明 gated sequence 对应的消费者还没有消费，需要等待
        else if (sequencer.getCursor() >= nextSequence)
        {
            // 返回 GATING，表示需要等待 gated sequence 提前
            return PollState.GATING;
        }
        // 否则，说明没有 event 需要消费
        // 即 nextSequence > availableSequence && sequencer.getCursor() < nextSequence
        // 即当前 poller 以及把所有消息都消费完了
        else
        {
            return PollState.IDLE;
        }
    }

    /**
     * Creates an event poller. Most users will want {@link RingBuffer#newPoller(Sequence...)}
     * which will set up the poller automatically
     *
     * <p>创建一个事件轮询器。大多数用户将希望使用{@link RingBuffer#newPoller(Sequence...)}，它将自动设置轮询器</p>
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
        // 这个 Sequence 是 new Sequence()，即一个新的 sequence，-1
        final Sequence sequence,
        final Sequence cursorSequence,
        final Sequence... gatingSequences)
    {
        // 封装 gatingSequences；
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

        return new EventPoller<>(dataProvider, sequencer, sequence, gatingSequence);
    }

    /**
     * Get the {@link Sequence} being used by this event poller
     *
     * <p>获取事件轮询器使用的{@link Sequence}</p>
     *
     * @return the sequence used by the event poller
     */
    public Sequence getSequence()
    {
        return sequence;
    }
}
