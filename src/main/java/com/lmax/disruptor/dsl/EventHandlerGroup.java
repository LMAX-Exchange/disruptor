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
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.BatchRewindStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.RewindableEventHandler;
import com.lmax.disruptor.RewindableException;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;

import java.util.Arrays;

/**
 * A group of {@link EventProcessor}s used as part of the {@link Disruptor}.
 *
 * <p>作为{@link Disruptor}的一部分使用的{@link EventProcessor}组。</p>
 *
 * @param <T> the type of entry used by the event processors.
 */
public class EventHandlerGroup<T>
{
    private final Disruptor<T> disruptor;
    private final ConsumerRepository consumerRepository;
    private final Sequence[] sequences;

    EventHandlerGroup(
        final Disruptor<T> disruptor,
        final ConsumerRepository consumerRepository,
        final Sequence[] sequences)
    {
        this.disruptor = disruptor;
        this.consumerRepository = consumerRepository;
        // sequences 是通过数组拷贝得到的，因此构造方法之后，外部没有办法修改 sequences 的内容
        this.sequences = Arrays.copyOf(sequences, sequences.length);
    }

    /**
     * Create a new event handler group that combines the consumers in this group with <code>otherHandlerGroup</code>.
     *
     * <p>创建一个新的 event handler group，将此组中的消费者与<code>otherHandlerGroup</code>组合。</p>
     *
     * @param otherHandlerGroup the event handler group to combine.
     * @return a new EventHandlerGroup combining the existing and new consumers into a single dependency group.
     */
    public EventHandlerGroup<T> and(final EventHandlerGroup<T> otherHandlerGroup)
    {
        // 合并 sequence 数组
        final Sequence[] combinedSequences = new Sequence[this.sequences.length + otherHandlerGroup.sequences.length];
        System.arraycopy(this.sequences, 0, combinedSequences, 0, this.sequences.length);
        System.arraycopy(
            otherHandlerGroup.sequences, 0,
            combinedSequences, this.sequences.length, otherHandlerGroup.sequences.length);
        // 返回一个新的 EventHandlerGroup
        return new EventHandlerGroup<>(disruptor, consumerRepository, combinedSequences);
    }

    /**
     * Create a new event handler group that combines the handlers in this group with <code>processors</code>.
     *
     * <p>创建一个新的 event handler group，将此组中的处理程序与<code>processors</code>组合。</p>
     *
     * @param processors the processors to combine.
     * @return a new EventHandlerGroup combining the existing and new processors into a single dependency group.
     */
    public EventHandlerGroup<T> and(final EventProcessor... processors)
    {
        Sequence[] combinedSequences = new Sequence[sequences.length + processors.length];

        for (int i = 0; i < processors.length; i++)
        {
            consumerRepository.add(processors[i]);
            combinedSequences[i] = processors[i].getSequence();
        }
        System.arraycopy(sequences, 0, combinedSequences, processors.length, sequences.length);

        return new EventHandlerGroup<>(disruptor, consumerRepository, combinedSequences);
    }

    /**
     * <p>Set up batch handlers to consume events from the ring buffer. These handlers will only process events
     * after every {@link EventProcessor} in this group has processed the event.</p>
     *
     * <p>设置批量消费的 handler 来从 ring buffer 中消费消息
     * 这些 handler 只会在当前 group 内的所有 handler 消费结束之后，才会进行消费</p>
     *
     * <p>This method is generally used as part of a chain. For example if the handler <code>A</code> must
     * process events before handler <code>B</code>:</p>
     *
     * <pre><code>dw.handleEventsWith(A).then(B);</code></pre>
     *
     * @param handlers the batch handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to set up a event processor barrier over the created event processors.
     */
    @SafeVarargs
    public final EventHandlerGroup<T> then(final EventHandler<? super T>... handlers)
    {
        return handleEventsWith(handlers);
    }

    /**
     * <p>Set up batch handlers to consume events from the ring buffer. These handlers will only process events
     * after every {@link EventProcessor} in this group has processed the event.</p>
     *
     * <p>This method is generally used as part of a chain. For example if the handler <code>A</code> must
     * process events before handler <code>B</code>:</p>
     *
     * <pre><code>dw.handleEventsWith(A).then(B);</code></pre>
     *
     * @param batchRewindStrategy a {@link BatchRewindStrategy} for customizing how to handle a {@link RewindableException}.
     * @param handlers            the rewindable event handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to set up a event processor barrier over the created event processors.
     */
    @SafeVarargs
    public final EventHandlerGroup<T> then(final BatchRewindStrategy batchRewindStrategy,
                                           final RewindableEventHandler<? super T>... handlers)
    {
        return handleEventsWith(batchRewindStrategy, handlers);
    }

    /**
     * <p>Set up custom event processors to handle events from the ring buffer. The Disruptor will
     * automatically start these processors when {@link Disruptor#start()} is called.</p>
     *
     * <p>This method is generally used as part of a chain. For example if the handler <code>A</code> must
     * process events before handler <code>B</code>:</p>
     *
     * @param eventProcessorFactories the event processor factories to use to create the event processors that will process events.
     * @return a {@link EventHandlerGroup} that can be used to chain dependencies.
     */
    @SafeVarargs
    public final EventHandlerGroup<T> then(final EventProcessorFactory<T>... eventProcessorFactories)
    {
        return handleEventsWith(eventProcessorFactories);
    }

    /**
     * <p>Set up batch handlers to handle events from the ring buffer. These handlers will only process events
     * after every {@link EventProcessor} in this group has processed the event.</p>
     *
     * <p>This method is generally used as part of a chain. For example if <code>A</code> must
     * process events before <code>B</code>:</p>
     *
     * <pre><code>dw.after(A).handleEventsWith(B);</code></pre>
     *
     * @param handlers the batch handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to set up a event processor barrier over the created event processors.
     */
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventHandler<? super T>... handlers)
    {
        // 将当前 group 中的 sequences 作为 barrier，进而创建 event processor
        return disruptor.createEventProcessors(sequences, handlers);
    }

    /**
     * <p>Set up batch handlers to handle events from the ring buffer. These handlers will only process events
     * after every {@link EventProcessor} in this group has processed the event.</p>
     *
     * <p>This method is generally used as part of a chain. For example if <code>A</code> must
     * process events before <code>B</code>:</p>
     *
     * <pre><code>dw.after(A).handleEventsWith(B);</code></pre>
     *
     * @param batchRewindStrategy a {@link BatchRewindStrategy} for customizing how to handle a {@link RewindableException}.
     * @param handlers            the rewindable event handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to set up a event processor barrier over the created event processors.
     */
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final BatchRewindStrategy batchRewindStrategy,
                                                       final RewindableEventHandler<? super T>... handlers)
    {
        return disruptor.createEventProcessors(sequences, batchRewindStrategy, handlers);
    }

    /**
     * <p>Set up custom event processors to handle events from the ring buffer. The Disruptor will
     * automatically start these processors when {@link Disruptor#start()} is called.</p>
     *
     * <p>This method is generally used as part of a chain. For example if <code>A</code> must
     * process events before <code>B</code>:</p>
     *
     * <pre><code>dw.after(A).handleEventsWith(B);</code></pre>
     *
     * @param eventProcessorFactories the event processor factories to use to create the event processors that will process events.
     * @return a {@link EventHandlerGroup} that can be used to chain dependencies.
     */
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventProcessorFactory<T>... eventProcessorFactories)
    {
        return disruptor.createEventProcessors(sequences, eventProcessorFactories);
    }

    /**
     * Create a dependency barrier for the processors in this group.
     * This allows custom event processors to have dependencies on
     * {@link com.lmax.disruptor.BatchEventProcessor}s created by the disruptor.
     *
     * <p>为此组中的处理器创建一个依赖关系屏障。
     * 这允许自定义事件处理器依赖于 disruptor 创建的{@link com.lmax.disruptor.BatchEventProcessor}。</p>
     *
     * @return a {@link SequenceBarrier} including all the processors in this group.
     */
    public SequenceBarrier asSequenceBarrier()
    {
        // 将当前 group 中的 sequences 封装，作为一个 barrier 对象
        return disruptor.getRingBuffer().newBarrier(sequences);
    }
}
