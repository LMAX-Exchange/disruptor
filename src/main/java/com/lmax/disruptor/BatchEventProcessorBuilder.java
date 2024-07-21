/*
 * Copyright 2023 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lmax.disruptor;

public final class BatchEventProcessorBuilder
{
    // 注意它不是静态的
    private int maxBatchSize = Integer.MAX_VALUE;

    /**
     * Set the maximum number of events that will be processed in a batch before updating the sequence.
     *
     * <p>设置在更新序列之前将在批处理中处理的事件的最大数量。</p>
     *
     * @param maxBatchSize max number of events to process in one batch.
     * @return The builder
     */
    public BatchEventProcessorBuilder setMaxBatchSize(final int maxBatchSize)
    {
        this.maxBatchSize = maxBatchSize;
        return this;
    }

    /**
     * Construct a {@link EventProcessor} that will automatically track the progress by updating its sequence when
     * the {@link EventHandler#onEvent(Object, long, boolean)} method returns.
     *
     * <p>构造一个{@link EventProcessor}，当{@link EventHandler#onEvent(Object, long, boolean)}方法返回时，它将自动跟踪进度，通过更新其序列。</p>
     *
     * <p>The created {@link BatchEventProcessor} will not support batch rewind,
     * but {@link EventHandler#setSequenceCallback(Sequence)} will be supported.
     *
     * <p>创建的{@link BatchEventProcessor}将不支持批量倒带，但将支持{@link EventHandler#setSequenceCallback(Sequence)}。</p>
     *
     * @param dataProvider    to which events are published.
     * @param sequenceBarrier on which it is waiting.
     * @param eventHandler    is the delegate to which events are dispatched.
     * @param <T>             event implementation storing the data for sharing during exchange or parallel coordination of an event.
     * @return the BatchEventProcessor
     */
    public <T> BatchEventProcessor<T> build(
            final DataProvider<T> dataProvider,
            final SequenceBarrier sequenceBarrier,
            final EventHandler<? super T> eventHandler)
    {
        final BatchEventProcessor<T> processor = new BatchEventProcessor<>(
                dataProvider, sequenceBarrier, eventHandler, maxBatchSize, null
        );
        // 从 processor 中取出 sequence 对象，设置到 eventHandler 中
        // 进而可以实现倒带
        eventHandler.setSequenceCallback(processor.getSequence());

        return processor;
    }

    /**
     * Construct a {@link EventProcessor} that will automatically track the progress by updating its sequence when
     * the {@link EventHandler#onEvent(Object, long, boolean)} method returns.
     *
     * @param dataProvider           to which events are published.
     * @param sequenceBarrier        on which it is waiting.
     * @param rewindableEventHandler is the delegate to which events are dispatched.
     * @param batchRewindStrategy    a {@link BatchRewindStrategy} for customizing how to handle a {@link RewindableException}.
     * @param <T>                    event implementation storing the data for sharing during exchange or parallel coordination of an event.
     * @return the BatchEventProcessor
     */
    public <T> BatchEventProcessor<T> build(
            final DataProvider<T> dataProvider,
            final SequenceBarrier sequenceBarrier,
            final RewindableEventHandler<? super T> rewindableEventHandler,
            final BatchRewindStrategy batchRewindStrategy)
    {
        if (null == batchRewindStrategy)
        {
            throw new NullPointerException("batchRewindStrategy cannot be null when building a BatchEventProcessor");
        }

        return new BatchEventProcessor<>(
                dataProvider, sequenceBarrier, rewindableEventHandler, maxBatchSize, batchRewindStrategy
        );
    }
}
