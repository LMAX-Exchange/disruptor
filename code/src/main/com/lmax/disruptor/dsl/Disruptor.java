/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.*;
import com.lmax.disruptor.util.Util;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A DSL-style API for setting up the disruptor pattern around a ring buffer.
 *
 * <p>A simple example of setting up the disruptor with two event handlers that must process events in order:</p>
 *
 * <pre><code> Disruptor<MyEvent> disruptor = new Disruptor<MyEvent>(MyEvent.FACTORY, 32, Executors.newCachedThreadPool());
 * EventHandler<MyEvent> handler1 = new EventHandler<MyEvent>() { ... };
 * EventHandler<MyEvent> handler2 = new EventHandler<MyEvent>() { ... };
 * disruptor.handleEventsWith(handler1);
 * disruptor.after(handler1).handleEventsWith(handler2);
 *
 * RingBuffer ringBuffer = disruptor.start();</code></pre>
 *
 * @param <T> the type of event used.
 */
public class Disruptor<T>
{
    private final RingBuffer<T> ringBuffer;
    private final Executor executor;
    private final EventProcessorRepository<T> eventProcessorRepository = new EventProcessorRepository<T>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final EventPublisher<T> eventPublisher;
    private ExceptionHandler exceptionHandler;

    /**
     * Create a new Disruptor.
     *
     * @param eventFactory   the factory to create events in the ring buffer.
     * @param ringBufferSize the size of the ring buffer.
     * @param executor       an {@link Executor} to execute event processors.
     */
    public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final Executor executor)
    {
        this(new RingBuffer<T>(eventFactory, ringBufferSize), executor);
    }

    /**
     * Create a new Disruptor.
     *
     * @param eventFactory   the factory to create events in the ring buffer.
     * @param executor       an {@link Executor} to execute event processors.
     * @param claimStrategy  the claim strategy to use for the ring buffer.
     * @param waitStrategy   the wait strategy to use for the ring buffer.
     */
    public Disruptor(final EventFactory<T> eventFactory, final Executor executor,
                     final ClaimStrategy claimStrategy,
                     final WaitStrategy waitStrategy)
    {
        this(new RingBuffer<T>(eventFactory, claimStrategy, waitStrategy), executor);
    }

    private Disruptor(final RingBuffer<T> ringBuffer, final Executor executor)
    {
        this.ringBuffer = ringBuffer;
        this.executor = executor;
        eventPublisher = new EventPublisher<T>(ringBuffer);
    }

    /**
     * Set up event handlers to handleEventException events from the ring buffer. These handlers will process events
     * as soon as they become available, in parallel.
     * <p/>
     * <p>This method can be used as the start of a chain. For example if the handler <code>A</code> must
     * process events before handler <code>B</code>:</p>
     * <p/>
     * <pre><code>dw.handleEventsWith(A).then(B);</code></pre>
     *
     * @param handlers the event handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to chain dependencies.
     */
    @SuppressWarnings("varargs")
    public EventHandlerGroup<T> handleEventsWith(final EventHandler<T>... handlers)
    {
        return createEventProcessors(new EventProcessor[0], handlers);
    }

    /**
     * Set up custom event processors to handleEventException events from the ring buffer. The Disruptor will
     * automatically start this processors when {@link #start()} is called.
     *
     * @param processors the event processors that will process events.
     * @return a {@link EventHandlerGroup} that can be used to chain dependencies.
     */
    public EventHandlerGroup<T> handleEventsWith(final EventProcessor... processors)
    {
        for (EventProcessor processor : processors)
        {
            eventProcessorRepository.add(processor);
        }
        return new EventHandlerGroup<T>(this, eventProcessorRepository, processors);
    }

    /**
     * Specify an exception handler to be used for any future event handlers.
     * Note that only event handlers set up after calling this method will use the exception handler.
     *
     * @param exceptionHandler the exception handler to use for any future {@link EventProcessor}.
     */
    public void handleExceptionsWith(final ExceptionHandler exceptionHandler)
    {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Override the default exception handler for a specific handler.
     * <pre>disruptorWizard.handleExceptionsIn(eventHandler).with(exceptionHandler);</pre>
     *
     * @param eventHandler the event handler to set a different exception handler for.
     * @return an ExceptionHandlerSetting dsl object - intended to be used by chaining the with method call.
     */
    public ExceptionHandlerSetting<?> handleExceptionsFor(final EventHandler<T> eventHandler)
    {
        return new ExceptionHandlerSetting<T>(eventHandler, eventProcessorRepository);
    }

    /**
     * Create a group of event handlers to be used as a dependency.
     * For example if the handler <code>A</code> must process events before handler <code>B</code>:
     * <p/>
     * <pre><code>dw.after(A).handleEventsWith(B);</code></pre>
     *
     * @param handlers the event handlers, previously set up with {@link #handleEventsWith(com.lmax.disruptor.EventHandler[])},
     *                 that will form the barrier for subsequent handlers or processors.
     * @return an {@link EventHandlerGroup} that can be used to setup a dependency barrier over the specified event handlers.
     */
    @SuppressWarnings("varargs")
    public EventHandlerGroup<T> after(final EventHandler<T>... handlers)
    {
        EventProcessor[] selectedEventProcessors = new EventProcessor[handlers.length];
        for (int i = 0, handlersLength = handlers.length; i < handlersLength; i++)
        {
            selectedEventProcessors[i] = eventProcessorRepository.getEventProcessorFor(handlers[i]);
        }

        return new EventHandlerGroup<T>(this, eventProcessorRepository, selectedEventProcessors);
    }

    /**
     * Create a group of event processors to be used as a dependency.
     *
     * @param processors the event processors, previously set up with {@link #handleEventsWith(com.lmax.disruptor.EventProcessor...)},
     *                   that will form the barrier for subsequent handlers or processors.
     * @return an {@link EventHandlerGroup} that can be used to setup a {@link SequenceBarrier} over hte specified event processors.
     * @see #after(com.lmax.disruptor.EventHandler[])
     */
    public EventHandlerGroup<T> after(final EventProcessor... processors)
    {
        for (EventProcessor processor : processors)
        {
            eventProcessorRepository.add(processor);
        }

        return new EventHandlerGroup<T>(this, eventProcessorRepository, processors);
    }

    /**
     * Publish an event to the ring buffer.
     *
     * @param eventTranslator the translator that will load data into the event.
     */
    public void publishEvent(final EventTranslator<T> eventTranslator)
    {
        eventPublisher.publishEvent(eventTranslator);
    }

    /**
     * Starts the event processors and returns the fully configured ring buffer.
     * The ring buffer is set up to prevent overwriting any entry that is yet to
     * be processed by the slowest event processor.
     * This method must only be called once after all event processors have been added.
     *
     * @return the configured ring buffer.
     */
    public RingBuffer<T> start()
    {
        EventProcessor[] gatingProcessors = eventProcessorRepository.getLastEventProcessorsInChain();
        ringBuffer.setGatingSequences(Util.getSequencesFor(gatingProcessors));

        checkOnlyStartedOnce();
        for (EventProcessorInfo<T> eventProcessorInfo : eventProcessorRepository)
        {
            executor.execute(eventProcessorInfo.getEventProcessor());
        }

        return ringBuffer;
    }

    /**
     * Calls {@link com.lmax.disruptor.EventProcessor#halt()} on all of the event processors created via this disruptor.
     */
    public void halt()
    {
        for (EventProcessorInfo<?> eventprocessorInfo : eventProcessorRepository)
        {
            eventprocessorInfo.getEventProcessor().halt();
        }
    }

    /**
     * Waits until all events currently in the disruptor have been processed by all event processors
     * and then halts the processors.  It is critical that publishing to the ring buffer has stopped
     * before calling this method, otherwise it may never return.
     *
     * <p>This method will not shutdown the executor, nor will it await the final termination of the
     * processor threads.</p>
     */
    public void shutdown()
    {
        while (hasBacklog())
        {
            // Busy spin
        }
        halt();
    }

    /**
     * The the {@link RingBuffer} used by this Disruptor.  This is useful for creating custom
     * event processors if the behaviour of {@link BatchEventProcessor} is not suitable.
     *
     * @return the ring buffer used by this Disruptor.
     */
    public RingBuffer<T> getRingBuffer()
    {
        return ringBuffer;
    }

    /**
     * Get the {@link SequenceBarrier} used by a specific handler. Note that the {@link SequenceBarrier}
     * may be shared by multiple event handlers.
     *
     * @param handler the handler to get the barrier for.
     * @return the SequenceBarrier used by <i>handler</i>.
     */
    public SequenceBarrier getBarrierFor(final EventHandler<T> handler)
    {
        return eventProcessorRepository.getBarrierFor(handler);
    }

    private boolean hasBacklog()
    {
        final long cursor = ringBuffer.getCursor();
        for (EventProcessor consumer : eventProcessorRepository.getLastEventProcessorsInChain())
        {
            if (cursor != consumer.getSequence().get())
            {
                return true;
            }
        }
        return false;
    }

    EventHandlerGroup<T> createEventProcessors(final EventProcessor[] barrierEventProcessors,
                                               final EventHandler<T>[] eventHandlers)
    {
        checkNotStarted();

        final EventProcessor[] createdEventProcessors = new EventProcessor[eventHandlers.length];
        final SequenceBarrier barrier = ringBuffer.newBarrier(Util.getSequencesFor(barrierEventProcessors));

        for (int i = 0, eventHandlersLength = eventHandlers.length; i < eventHandlersLength; i++)
        {
            final EventHandler<T> eventHandler = eventHandlers[i];

            final BatchEventProcessor<T> batchEventProcessor = new BatchEventProcessor<T>(ringBuffer, barrier, eventHandler);

            if (exceptionHandler != null)
            {
                batchEventProcessor.setExceptionHandler(exceptionHandler);
            }

            eventProcessorRepository.add(batchEventProcessor, eventHandler, barrier);
            createdEventProcessors[i] = batchEventProcessor;
        }

        if (createdEventProcessors.length > 0)
        {
            eventProcessorRepository.unMarkEventProcessorsAsEndOfChain(barrierEventProcessors);
        }

        return new EventHandlerGroup<T>(this, eventProcessorRepository, createdEventProcessors);
    }

    private void checkNotStarted()
    {
        if (started.get())
        {
            throw new IllegalStateException("All event handlers must be added before calling starts.");
        }
    }

    private void checkOnlyStartedOnce()
    {
        if (!started.compareAndSet(false, true))
        {
            throw new IllegalStateException("Disruptor.start() must only be called once.");
        }
    }
}
