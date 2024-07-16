package com.lmax.disruptor;

/**
 * Operations related to the sequencing of items in a {@link RingBuffer}.
 * See the two child interfaces, {@link Sequencer} and {@link EventSequencer} for more details.
 *
 * <p>与{@link RingBuffer}中的项目排序相关的操作。
 * 有关更多详细信息，请参见两个子接口{@link Sequencer}和{@link EventSequencer}。</p>
 */
public interface Sequenced
{
    /**
     * The capacity of the data structure to hold entries.
     *
     * <p>数据结构容纳条目的容量。</p>
     *
     * @return the size of the RingBuffer.
     */
    int getBufferSize();

    /**
     * Has the buffer got capacity to allocate another sequence.  This is a concurrent
     * method so the response should only be taken as an indication of available capacity.
     *
     * <p>缓冲区是否有容量分配另一个序列。
     * 这是一个并发方法，因此响应应仅被视为可用容量的指示。</p>
     *
     * @param requiredCapacity in the buffer
     * @return true if the buffer has the capacity to allocate the next sequence otherwise false.
     */
    boolean hasAvailableCapacity(int requiredCapacity);

    /**
     * Get the remaining capacity for this sequencer.
     *
     * <p>获取此顺序器的剩余容量。</p>
     *
     * @return The number of slots remaining.
     */
    long remainingCapacity();

    /**
     * Claim the next event in sequence for publishing.
     *
     * <p>声明要发布的序列中的下一个事件。</p>
     *
     * @return the claimed sequence value
     */
    long next();

    /**
     * Claim the next n events in sequence for publishing.  This is for batch event producing.  Using batch producing
     * requires a little care and some math.
     * <pre>
     * int n = 10;
     * long hi = sequencer.next(n);
     * long lo = hi - (n - 1);
     * for (long sequence = lo; sequence &lt;= hi; sequence++) {
     *     // Do work.
     * }
     * sequencer.publish(lo, hi);
     * </pre>
     *
     * <p>声明要发布的序列中的下一个n个事件。
     * 这是用于批量事件生成的。
     * 使用批量生成需要一些小心和一些数学。
     *
     *
     * @param n the number of sequences to claim
     * @return the highest claimed sequence value
     */
    long next(int n);

    /**
     * Attempt to claim the next event in sequence for publishing.  Will return the
     * number of the slot if there is at least <code>requiredCapacity</code> slots
     * available.
     *
     * <p>尝试声明要发布的序列中的下一个事件。
     * 如果至少有<code>requiredCapacity</code>个插槽可用，则将返回插槽的编号。</p>
     *
     * @return the claimed sequence value
     * @throws InsufficientCapacityException thrown if there is no space available in the ring buffer.
     */
    long tryNext() throws InsufficientCapacityException;

    /**
     * Attempt to claim the next n events in sequence for publishing.  Will return the
     * highest numbered slot if there is at least <code>requiredCapacity</code> slots
     * available.  Have a look at {@link Sequencer#next()} for a description on how to
     * use this method.
     *
     * <p>尝试声明要发布的序列中的下一个n个事件。
     * 如果至少有<code>requiredCapacity</code>个插槽可用，则将返回最高编号的插槽。
     * 请查看{@link Sequencer#next()}，了解如何使用此方法的描述。</p>
     *
     * @param n the number of sequences to claim
     * @return the claimed sequence value
     * @throws InsufficientCapacityException thrown if there is no space available in the ring buffer.
     */
    long tryNext(int n) throws InsufficientCapacityException;

    /**
     * Publishes a sequence. Call when the event has been filled.
     *
     * <p>发布序列。在事件填充时调用。</p>
     *
     * @param sequence the sequence to be published.
     */
    void publish(long sequence);

    /**
     * Batch publish sequences.  Called when all of the events have been filled.
     *
     * <p>批量发布序列。在所有事件都填充时调用。</p>
     *
     * @param lo first sequence number to publish
     * @param hi last sequence number to publish
     */
    void publish(long lo, long hi);
}