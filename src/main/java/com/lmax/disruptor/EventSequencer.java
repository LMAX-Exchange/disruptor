package com.lmax.disruptor;

/**
 * Pulls together the low-level data access and sequencing operations of {@link RingBuffer}
 *
 * <p>汇集{@link RingBuffer}的低级数据访问和排序操作</p>
 * @param <T> The event type
 */
public interface EventSequencer<T> extends DataProvider<T>, Sequenced
{

}
