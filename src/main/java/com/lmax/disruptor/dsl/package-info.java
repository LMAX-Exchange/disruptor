/**
 * A DSL-style API for setting up the disruptor pattern around a ring buffer.
 *
 * <h2>Example code</h2>
 * <pre>{@code
 * // Specify the size of the ring buffer, must be power of 2.
 *  int bufferSize = 1024;
 *
 *  // Construct the Disruptor
 *  Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);
 *
 *  // Connect the handler
 *  disruptor.handleEventsWith((event, sequence, endOfBatch) -> System.out.println("Event: " + event));
 *
 *  // Start the Disruptor, starts all threads running
 *  disruptor.start();
 *
 *  // Get the ring buffer from the Disruptor to be used for publishing.
 *  RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();
 *
 *  ByteBuffer bb = ByteBuffer.allocate(8);
 *  for (long l = 0; true; l++)
 *  {
 *      bb.putLong(0, l);
 *      ringBuffer.publishEvent((event, sequence, buffer) -> event.set(buffer.getLong(0)), bb);
 *      Thread.sleep(1000);
 *  }
 * }</pre>
 */
package com.lmax.disruptor.dsl;