/**
 * @(#)LongEventMain.java, 2024/7/11
 * <p/>
 * Copyright 2022 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package myexapmples.getstart;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.nio.ByteBuffer;

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

        Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);
        // 设置队列的 consumer
        disruptor
                .handleEventsWith((event, sequence, endOfBatch) ->
                        System.out.println("Event: " + event + " Sequence: " + sequence + " EndOfBatch: " + endOfBatch))
                .then((event, sequence, endOfBatch) -> event.setValue(0L)); // clear the event
        // 启动队列
        disruptor.start();

        // 取到 ringBuffer
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();
        ByteBuffer bb = ByteBuffer.allocate(8);

        for (int i = 0; true; i++)
        {
            // 假设 bb 为数据源/事件源
            bb.putLong(0, i);
            // 发布消息
            final int finalI = i;
            ringBuffer.publishEvent((event, sequence) -> event.setValue(finalI));
            Thread.sleep(1000);
        }
    }
}
