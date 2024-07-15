/**
 * @(#)LongEvent.java, 2024/7/11
 * <p/>
 * Copyright 2022 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package myexapmples.getstart;

/**
 * 定义 disruptor 中 publish 和 consume 的消息体
 *
 * @author zwb
 */
public class LongEvent
{
    private long value;

    public void setValue(long value)
    {
        this.value = value;
    }


//    @Override
//    public String toString()
//    {
//        return "LongEvent{" + "value=" + value + '}';
//    }
}
