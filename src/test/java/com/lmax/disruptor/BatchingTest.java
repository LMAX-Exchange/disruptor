package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.support.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.locks.LockSupport;

/**
 * 这个是参数化测试的方式
 * @RunWith(Parameterized.class)
 */
@RunWith(Parameterized.class)
public class BatchingTest
{
    private final ProducerType producerType;

    public BatchingTest(ProducerType producerType)
    {
        this.producerType = producerType;
    }

    /** 
     * 准备数据。数据的准备需要在一个方法中进行，该方法需要满足一定的要求： 
     *
     *    1）该方法必须由Parameters注解修饰 
     *    2）该方法必须为public static的 
     *    3）该方法必须返回Collection类型 
     *    4）该方法的名字不做要求 
     *    5）该方法没有参数 
     *    @return 
     */  
    @Parameters
    public static Collection<Object[]> generateData()
    {
        Object[][] producerTypes = {{ProducerType.MULTI}, {ProducerType.SINGLE}};
        return Arrays.asList(producerTypes);
    }

    private static class ParallelEventHandler implements EventHandler<LongEvent>
    {
        private final long mask;
        private final long ordinal;
        private final int batchSize = 10;

        private long eventCount;
        private long batchCount;
        private long publishedValue;
        private long tempValue;
        private volatile long processed;

        ParallelEventHandler(long mask, long ordinal)
        {
            this.mask = mask;
            this.ordinal = ordinal;
        }

        @Override
        public void onEvent(LongEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            if ((sequence & mask) == ordinal)
            {
                eventCount++;
                tempValue = event.get();
            }

            if (endOfBatch || ++batchCount >= batchSize)
            {
                publishedValue = tempValue;
                batchCount = 0;
            }
            else
            {
                LockSupport.parkNanos(1);
            }

            processed = sequence;
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldBatch() throws Exception
    {
        //初始化对象，参数实例，ringBufferSize个数，线程工厂，生产者类型(SINGLE or MULTI)，等待函数
        //这一步主要是为了创建：ringBuffer，那么ringBuffer是什么呢？
        Disruptor<LongEvent> d = new Disruptor<LongEvent>(
            LongEvent.FACTORY, 2048, DaemonThreadFactory.INSTANCE,
            producerType, new SleepingWaitStrategy());

        //创建handler
        ParallelEventHandler handler1 = new ParallelEventHandler(1, 0);
        ParallelEventHandler handler2 = new ParallelEventHandler(1, 1);

        //添加所有的事件handler
        d.handleEventsWith(handler1, handler2);

        //启动,实际上是启动了消费仓库中的所有进程，就是eventprocess
        RingBuffer<LongEvent> buffer = d.start();

        EventTranslator<LongEvent> translator = new EventTranslator<LongEvent>()
        {
            @Override
            public void translateTo(LongEvent event, long sequence)
            {
                event.set(sequence);
            }
        };

        int eventCount = 10000;
        for (int i = 0; i < eventCount; i++)
        {
            buffer.publishEvent(translator);
        }

        while (handler1.processed != eventCount - 1 ||
            handler2.processed != eventCount - 1)
        {
            Thread.sleep(1);
        }

        Assert.assertThat(handler1.publishedValue, CoreMatchers.is((long) eventCount - 2));
        Assert.assertThat(handler1.eventCount, CoreMatchers.is((long) eventCount / 2));
        Assert.assertThat(handler2.publishedValue, CoreMatchers.is((long) eventCount - 1));
        Assert.assertThat(handler2.eventCount, CoreMatchers.is((long) eventCount / 2));
    }
}
