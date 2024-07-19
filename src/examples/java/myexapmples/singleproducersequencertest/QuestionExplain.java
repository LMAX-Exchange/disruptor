/**
 * @(#)QuestionExplain.java, 2024/7/19
 * <p/>
 * Copyright 2022 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package myexapmples.singleproducersequencertest;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.examples.longevent.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * @author zwb
 */
public class QuestionExplain
{
    public static void main(String[] args)
    {
        int bufferSize = 1024;
        Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, new BlockingWaitStrategy());

        disruptor
                .handleEventsWith((event, sequence, endOfBatch) ->
                        {
                            // pretend to be a very slow consumer
                            Thread.sleep(10000L);
                            System.out.println("Event: " + event + " Sequence: " + sequence + " EndOfBatch: " + endOfBatch);
                        }
                );
        disruptor.start();

        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.next(1000);
        // in the next call, sequencer#curosr will be set to 999, but I've not published the events yet
        ringBuffer.next(1000);
    }
}
