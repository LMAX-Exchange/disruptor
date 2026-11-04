/**
 * The Disruptor is a concurrent programming framework for exchanging and coordinating work as a continuous series of events.
 * It can be used as an alternative to wiring processing stages together via queues.
 * The Disruptor design has the characteristics of generating significantly less garbage than queues and separates the
 * concurrency concerns so non-locking algorithms can be employed resulting in greater scalability and performance.
 *
 * <p>It works on the principle of having a number of stages that are each single threaded with local state and memory.
 * No global memory exists and all communication is achieved by passing messages/state via managed ring buffers.
 *
 * <p>Almost any graph or pipeline structure can be composed via one or more Disruptor patterns.
 *
 * <h2>UniCast a series of items between 1 publisher and 1 EventProcessor.</h2>
 *
 * <pre>{@code
 *                                           track to prevent wrap
 *                                           +------------------+
 *                                           |                  |
 *                                           |                  v
 * +----+    +-----+            +----+    +====+    +====+   +-----+
 * | P1 |--->| EP1 |            | P1 |--->| RB |<---| SB |   | EP1 |
 * +----+    +-----+            +----+    +====+    +====+   +-----+
 *                                   claim      get    ^        |
 *                                                     |        |
 *                                                     +--------+
 *                                                       waitFor
 * }</pre>
 *
 * <h2>Sequence a series of messages from multiple publishers</h2>
 * <pre>{@code
 *                                          track to prevent wrap
 *                                          +--------------------+
 *                                          |                    |
 *                                          |                    v
 * +----+                       +----+    +====+    +====+    +-----+
 * | P1 |-------+               | P1 |--->| RB |<---| SB |    | EP1 |
 * +----+       |               +----+    +====+    +====+    +-----+
 *              v                           ^   get    ^         |
 * +----+    +-----+            +----+      |          |         |
 * | P2 |--->| EP1 |            | P2 |------+          +---------+
 * +----+    +-----+            +----+      |            waitFor
 *              ^                           |
 * +----+       |               +----+      |
 * | P3 |-------+               | P3 |------+
 * +----+                       +----+
 * }</pre>
 *
 * <h2>Pipeline a series of messages</h2>
 * <pre>{@code
 *                           +----+    +-----+    +-----+    +-----+
 *                           | P1 |--->| EP1 |--->| EP2 |--->| EP3 |
 *                           +----+    +-----+    +-----+    +-----+
 *
 *
 *
 *                           track to prevent wrap
 *              +----------------------------------------------------------------+
 *              |                                                                |
 *              |                                                                v
 * +----+    +====+    +=====+    +-----+    +=====+    +-----+    +=====+    +-----+
 * | P1 |--->| RB |    | SB1 |<---| EP1 |<---| SB2 |<---| EP2 |<---| SB3 |<---| EP3 |
 * +----+    +====+    +=====+    +-----+    +=====+    +-----+    +=====+    +-----+
 *      claim   ^  get    |   waitFor           |   waitFor           |  waitFor
 *              |         |                     |                     |
 *              +---------+---------------------+---------------------+
 * }</pre>
 *
 * <h2>Multicast a series of messages to multiple EventProcessors</h2>
 * <pre>{@code
 *           +-----+                                        track to prevent wrap
 *    +----->| EP1 |                        +--------------------+----------+----------+
 *    |      +-----+                        |                    |          |          |
 *    |                                     |                    v          v          v
 * +----+    +-----+            +----+    +====+    +====+    +-----+    +-----+    +-----+
 * | P1 |--->| EP2 |            | P1 |--->| RB |<---| SB |    | EP1 |    | EP2 |    | EP3 |
 * +----+    +-----+            +----+    +====+    +====+    +-----+    +-----+    +-----+
 *    |                              claim      get    ^         |          |          |
 *    |      +-----+                                   |         |          |          |
 *    +----->| EP3 |                                   +---------+----------+----------+
 *           +-----+                                                 waitFor
 * }</pre>
 *
 * <h2>Replicate a message then fold back the results</h2>
 * <pre>{@code
 *           +-----+                               track to prevent wrap
 *    +----->| EP1 |-----+                   +-------------------------------+
 *    |      +-----+     |                   |                               |
 *    |                  v                   |                               v
 * +----+             +-----+   +----+    +====+               +=====+    +-----+
 * | P1 |             | EP3 |   | P1 |--->| RB |<--------------| SB2 |<---| EP3 |
 * +----+             +-----+   +----+    +====+               +=====+    +-----+
 *    |                  ^           claim   ^  get               |   waitFor
 *    |      +-----+     |                   |                    |
 *    +----->| EP2 |-----+                +=====+    +-----+      |
 *           +-----+                      | SB1 |<---| EP1 |<-----+
 *                                        +=====+    +-----+      |
 *                                           ^                    |
 *                                           |       +-----+      |
 *                                           +-------| EP2 |<-----+
 *                                          waitFor  +-----+
 * }</pre>
 *
 * <h2>Code Example</h2>
 *
 * <p>This complete example publishes one value, waits for it to be processed, and stops the processor.
 *
 * <pre>{@code
 * import com.lmax.disruptor.BatchEventProcessor;
 * import com.lmax.disruptor.BatchEventProcessorBuilder;
 * import com.lmax.disruptor.EventHandler;
 * import com.lmax.disruptor.RingBuffer;
 * import com.lmax.disruptor.SequenceBarrier;
 * import com.lmax.disruptor.SleepingWaitStrategy;
 *
 * import java.util.concurrent.CountDownLatch;
 *
 * public class DisruptorExample
 * {
 *     public static final class ValueEvent
 *     {
 *         private long value;
 *
 *         public long getValue()
 *         {
 *             return value;
 *         }
 *
 *         public void setValue(final long value)
 *         {
 *             this.value = value;
 *         }
 *     }
 *
 *     public static void main(final String[] args) throws InterruptedException
 *     {
 *         final int bufferSize = 1024;
 *         final CountDownLatch processed = new CountDownLatch(1);
 *         final EventHandler<ValueEvent> eventHandler = (event, sequence, endOfBatch) ->
 *         {
 *             System.out.println(event.getValue());
 *             processed.countDown();
 *         };
 *
 *         final RingBuffer<ValueEvent> ringBuffer =
 *             RingBuffer.createSingleProducer(ValueEvent::new, bufferSize, new SleepingWaitStrategy());
 *         final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
 *         final BatchEventProcessor<ValueEvent> batchProcessor =
 *             new BatchEventProcessorBuilder().build(ringBuffer, sequenceBarrier, eventHandler);
 *         ringBuffer.addGatingSequences(batchProcessor.getSequence());
 *
 *         // Each processor runs on a separate thread.
 *         final Thread processorThread = new Thread(batchProcessor);
 *         processorThread.start();
 *         try
 *         {
 *             // Publishers claim events in sequence and always publish the claimed slot.
 *             final long sequence = ringBuffer.next();
 *             try
 *             {
 *                 ringBuffer.get(sequence).setValue(1234);
 *             }
 *             finally
 *             {
 *                 ringBuffer.publish(sequence);
 *             }
 *
 *             // Wait for the event before stopping its processor.
 *             processed.await();
 *         }
 *         finally
 *         {
 *             batchProcessor.halt();
 *             processorThread.join();
 *         }
 *     }
 * }
 * }</pre>
 */
package com.lmax.disruptor;
