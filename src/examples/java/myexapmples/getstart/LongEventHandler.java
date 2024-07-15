/**
 * @(#)LongEventHandler.java, 2024/7/11
 * <p/>
 * Copyright 2022 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package myexapmples.getstart;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.Sequence;

/**
 * 定义一个事件处理器，用于处理 LongEvent；
 * 它只需要实现 onEvent 来处理时间即可，不需要考虑取值、同步等问题；
 *
 * @author zwb
 */
public class LongEventHandler implements EventHandler<LongEvent>
{
    @Override
    public void onEvent(final LongEvent event, final long sequence, final boolean endOfBatch) throws Exception
    {
        System.out.println("Event: " + event + " Sequence: " + sequence + " EndOfBatch: " + endOfBatch);
    }

    @Override
    public void setSequenceCallback(final Sequence sequenceCallback)
    {
        EventHandler.super.setSequenceCallback(sequenceCallback);
    }
}
