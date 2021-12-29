package com.lmax.disruptor.sequence;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.provider.DataProvider;

/**
 * Pulls together the low-level data access and sequencing operations of {@link RingBuffer}
 * @param <T> The event type
 */
public interface EventSequencer<T> extends DataProvider<T>, Sequenced
{

}
