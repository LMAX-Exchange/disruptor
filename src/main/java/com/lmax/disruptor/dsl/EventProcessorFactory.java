package com.lmax.disruptor.dsl;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;

/**
 * A factory interface to make it possible to include custom event processors in a chain:
 *
 * <pre><code>
 * disruptor.handleEventsWith(handler1).then((ringBuffer, barrierSequences) -&gt; new CustomEventProcessor(ringBuffer, barrierSequences));
 * </code></pre>
 *
 * <p>一个工厂接口，使得可以在链中包含自定义的 event processor：</p>
 * <pre><code>disruptor.handleEventsWith(handler1).then((ringBuffer, barrierSequences) -&gt; new CustomEventProcessor(ringBuffer, barrierSequences));</code></pre>
 *
 * @param <T> implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface EventProcessorFactory<T>
{
    /**
     * Create a new event processor that gates on <code>barrierSequences</code>.
     *
     * <p>创建一个新的 event processor，它在<code>barrierSequences</code>上进行门控。</p>
     *
     * @param ringBuffer the ring buffer to receive events from.
     * @param barrierSequences the sequences to gate on
     * @return a new EventProcessor that gates on <code>barrierSequences</code> before processing events
     */
    EventProcessor createEventProcessor(RingBuffer<T> ringBuffer, Sequence[] barrierSequences);
}
