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
package com.lmax.disruptor;


/**
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy.
 *
 * <p>用于在游标序列和可选的依赖{@link EventProcessor}(s)上对{@link EventProcessor}进行分组的{@link SequenceBarrier}，使用给定的WaitStrategy。</p>
 */
final class ProcessingSequenceBarrier implements SequenceBarrier
{
    // 等待策略
    private final WaitStrategy waitStrategy;
    // 代表 barrier 需要管理的那些 sequences 数组
    // 会在构造方法中封装为一个 sequenceGroup 对象
    private final Sequence dependentSequence;
    // 异常状态标识
    private volatile boolean alerted = false;
    // 代表 publisher 的游标
    private final Sequence cursorSequence;
    private final Sequencer sequencer;

    ProcessingSequenceBarrier(
        final Sequencer sequencer,
        final WaitStrategy waitStrategy,
        final Sequence cursorSequence,
        final Sequence[] dependentSequences)
    {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        // 如果入参是空数组，说明 barrier 不需要管理任何消费者的 sequence（当然其实不限制只是消费者）
        if (0 == dependentSequences.length)
        {
            dependentSequence = cursorSequence;
        }
        else
        {
            // 将入参的 sequences 封装为一个 sequenceGroup 对象
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException, TimeoutException
    {
        // 校验是否处于警报状态，如果是的话，抛出异常
        checkAlert();

        // 触发 waitStrategy 的 waitFor 方法来进行等待
        // 方法返回 waitFor 结束之后的 sequence 值
        // 这个 availableSequence 其实比较 ambiguous，它表示 dependentSequence 中最小的 sequence 值
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);

        // 如果 availableSequence 小于方法入参要求的 sequence，说明 dependentSequence 中的 sequence 值还没有达到 sequence
        if (availableSequence < sequence)
        {
            return availableSequence;
        }

        // 否则，说明本次 waitFor 方法等待非常有效，那么会返回最新的 publish 的 event 对应的 sequence 值
        // 即返回最新可消费的消息的 sequence 值

        // MultiProducerSequencer 之所以在 next 的时候更新 cursor 不会出问题，
        // 就是因为这里通过 getHighestPublishedSequence 来计算结果的时候，MultiProducerSequencer 内部会额外判断一次 isAvailable 方法
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert()
    {
        // 设置警报状态，表示发生了异常
        alerted = true;
        // 唤醒所有等待的线程，主要是调用这个方法的不一定是 barrier 对应的消费者线程
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException
    {
        if (alerted)
        {
            throw AlertException.INSTANCE;
        }
    }
}