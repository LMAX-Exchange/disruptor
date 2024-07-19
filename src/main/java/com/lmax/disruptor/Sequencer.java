/*
 * Copyright 2012 LMAX Ltd.
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
package com.lmax.disruptor;

/**
 * Coordinates claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 *
 * <p>协调声明序列以访问数据结构，同时跟踪依赖的{@link Sequence}。</p>
 */
public interface Sequencer extends Cursored, Sequenced
{
    /**
     * Set to -1 as sequence starting point
     *
     * <p>设置为-1作为序列起点</p>
     */
    long INITIAL_CURSOR_VALUE = -1L;

    /**
     * Claim a specific sequence.  Only used if initialising the ring buffer to
     * a specific value.
     *
     * <p>声明特定序列值。 仅在将环形缓冲区初始化为特定值时使用。</p>
     *
     * @param sequence The sequence to initialise too.
     */
    void claim(long sequence);

    /**
     * Confirms if a sequence is published and the event is available for use; non-blocking.
     *
     * <p>确认序列是否已发布并且事件可供使用； 非阻塞。</p>
     *
     * @param sequence of the buffer to check
     * @return true if the sequence is available for use, false if not
     */
    boolean isAvailable(long sequence);

    /**
     * Add the specified gating sequences to this instance of the Disruptor.  They will
     * safely and atomically added to the list of gating sequences.
     *
     * <p>将指定的门控序列添加到Disruptor的此实例。 它们将安全且原子地添加到门控序列列表。</p>
     * 所谓 gating 就是指消费者的 sequence，表示消费者的进度不能超过生产者。
     *
     * @param gatingSequences The sequences to add.
     */
    void addGatingSequences(Sequence... gatingSequences);

    /**
     * Remove the specified sequence from this sequencer.
     *
     * <p>从此顺序器中删除指定的序列。</p>
     *
     * @param sequence to be removed.
     * @return <code>true</code> if this sequence was found, <code>false</code> otherwise.
     */
    boolean removeGatingSequence(Sequence sequence);

    /**
     * Create a new SequenceBarrier to be used by an EventProcessor to track which messages
     * are available to be read from the ring buffer given a list of sequences to track.
     *
     * <p>创建一个新的SequenceBarrier，供EventProcessor使用，以跟踪可以从环形缓冲区中读取的消息，
     *
     * @param sequencesToTrack All of the sequences that the newly constructed barrier will wait on.
     * @return A sequence barrier that will track the specified sequences.
     * @see SequenceBarrier
     */
    SequenceBarrier newBarrier(Sequence... sequencesToTrack);

    /**
     * Get the minimum sequence value from all of the gating sequences
     * added to this ringBuffer.
     *
     * <p>从添加到此ringBuffer的所有 gating sequences 中获取最小序列值。</p>
     *
     * @return The minimum gating sequence or the cursor sequence if
     * no sequences have been added.
     */
    long getMinimumSequence();

    /**
     * Get the highest sequence number that can be safely read from the ring buffer.  Depending
     * on the implementation of the Sequencer this call may need to scan a number of values
     * in the Sequencer.  The scan will range from nextSequence to availableSequence.  If
     * there are no available values <code>&gt;= nextSequence</code> the return value will be
     * <code>nextSequence - 1</code>.  To work correctly a consumer should pass a value that
     * is 1 higher than the last sequence that was successfully processed.
     *
     * <p>获取可以安全读取的环形缓冲区中的最高序列号。
     * 根据Sequencer的实现，此调用可能需要扫描Sequencer中的多个值。
     * 扫描范围从nextSequence到availableSequence。
     * 如果没有availableSequence>=nextSequence，则返回值将为 nextSequence - 1。
     * 为了正确工作，消费者应传递一个比最后成功处理的序列号高1的值。</p>
     *
     * @param nextSequence      The sequence to start scanning from.
     * @param availableSequence The sequence to scan to.
     * @return The highest value that can be safely read, will be at least <code>nextSequence - 1</code>.
     */
    long getHighestPublishedSequence(long nextSequence, long availableSequence);

    /**
     * Creates an event poller from this sequencer
     *
     * <p>从此顺序器创建事件轮询器</p>
     *
     * @param provider from which events are drawn
     * @param gatingSequences sequences to be gated on
     * @param <T> the type of the event
     * @return the event poller
     */
    <T> EventPoller<T> newPoller(DataProvider<T> provider, Sequence... gatingSequences);
}