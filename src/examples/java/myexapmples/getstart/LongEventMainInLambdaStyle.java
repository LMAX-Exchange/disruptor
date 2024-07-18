/**
 * @(#)LongEventMain.java, 2024/7/11
 * <p/>
 * Copyright 2022 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package myexapmples.getstart;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * 使用 lambda 风格的 api 来编写 publisher 的实现
 *
 * @author zwb
 */
public class LongEventMainInLambdaStyle
{
    public static void main(String[] args) throws InterruptedException
    {
        // 构造队列
        int bufferSize = 1024;

        Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, new BlockingWaitStrategy());
        // 设置队列的 consumer
        disruptor
                .handleEventsWith((event, sequence, endOfBatch) ->
                        System.out.println("Event: " + event + " Sequence: " + sequence + " EndOfBatch: " + endOfBatch))
                .then((event, sequence, endOfBatch) -> event.setValue(0L)); // clear the event
        // 启动队列
        disruptor.start();

        // 取到 ringBuffer
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        ringBuffer.hasAvailableCapacity(100);

        for (int i = 0; i < 10000; i++)
        {
            ringBuffer.publishEvent((event, sequence) -> event.setValue(1L));
        }
        System.out.println("main:" + ringBuffer.hasAvailableCapacity(100));
    }
}
