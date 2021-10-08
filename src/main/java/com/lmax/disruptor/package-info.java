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
 * <pre>{@code
 * // Event holder for data to be exchanged
 * public final class ValueEvent
 * {
 *     private long value;
 *
 *     public long getValue()
 *     {
 *         return value;
 *     }
 *
 *     public void setValue(final long value)
 *     {
 *         this.value = value;
 *     }
 *
 *     public final static EventFactory<ValueEvent> EVENT_FACTORY = new EventFactory<ValueEvent>()
 *     {
 *         public ValueEvent newInstance()
 *         {
 *             return new ValueEvent();
 *         }
 *     };
 * }
 *
 * // Callback handler which can be implemented by EventProcessors
 * final EventHandler<ValueEvent> eventHandler = new EventHandler<ValueEvent>()
 * {
 *     public void onEvent(final ValueEvent event, final long sequence, final boolean endOfBatch)
 *         throws Exception
 *     {
 *         // process a new event as it becomes available.
 *     }
 * };
 *
 * RingBuffer<ValueEvent> ringBuffer =
 *     new RingBuffer<ValueEvent>(ValueEvent.EVENT_FACTORY,
 *                                new SingleThreadedClaimStrategy(BUFFER_SIZE),
 *                                new SleepingWaitStrategy());
 *
 * SequenceBarrier<ValueEvent> sequenceBarrier = ringBuffer.newBarrier();
 * BatchEventProcessor<ValueEvent> batchProcessor = new BatchEventProcessor<ValueEvent>(sequenceBarrier, eventHandler);
 * ringBuffer.setGatingSequences(batchProcessor.getSequence());
 *
 * // Each processor runs on a separate thread
 * EXECUTOR.submit(batchProcessor);
 *
 * // Publishers claim events in sequence
 * long sequence = ringBuffer.next();
 * ValueEvent event = ringBuffer.get(sequence);
 *
 * event.setValue(1234);
 *
 * // publish the event so it is available to EventProcessors
 * ringBuffer.publish(sequence);
 * }</pre>
 */
package com.lmax.disruptor;