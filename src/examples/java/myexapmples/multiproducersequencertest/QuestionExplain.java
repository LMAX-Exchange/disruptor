/**
 * @(#)QuestionExplain.java, 2024/7/22
 * <p/>
 * Copyright 2022 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package myexapmples.multiproducersequencertest;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.examples.longevent.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * @author zwb
 */
public class QuestionExplain
{
    public static void main(String[] args) throws Exception
    {
        int bufferSize = 1024;
        Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new BlockingWaitStrategy());

        disruptor.handleEventsWith((event, sequence, endOfBatch) ->
                System.out.println("Event: " + event + " Sequence: " + sequence + " EndOfBatch: " + endOfBatch));

        disruptor.start();

        EventPoller<LongEvent> poller = disruptor.getRingBuffer().newPoller();
        new Thread(() -> {
            while (true) {
                try
                {
                    poller.poll((event, sequence, endOfBatch) ->
                    {
                        // pretend to consume very slowly
                        Thread.sleep(10000L);
                        System.out.println("Event: " + event + " Sequence: " + sequence + " EndOfBatch: " + endOfBatch);
                        return true;
                    });
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }).start();


        // the entry[] will be filled in 2 rounds
        for (int i = 0; i < 3000; i++)
        {
            final long value = i;
            disruptor.getRingBuffer().publishEvent((event, sequence) -> event.set(value));
        }
    }
}
