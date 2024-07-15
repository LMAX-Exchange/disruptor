/**
 * @(#)LongEventFactory.java, 2024/7/11
 * <p/>
 * Copyright 2022 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package myexapmples.getstart;

import com.lmax.disruptor.EventFactory;

/**
 * 定义一个工厂类来实例化 LongEvent
 *
 * @author zwb
 */
public class LongEventFactory implements EventFactory<LongEvent>
{
    @Override
    public LongEvent newInstance()
    {
        return new LongEvent();
    }
}
