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
package com.lmax.disruptor.wizard;

import com.lmax.disruptor.*;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A DSL-style wizard for setting up the disruptor pattern around a ring buffer.
 * <p/>
 * <p>A simple example of setting up the disruptor with two event handlers that must process events in order:</p>
 * <p/>
 * <pre><code> DisruptorWizard<MyEvent> dw = new DisruptorWizard<MyEvent>(MyEvent.FACTORY, 32, Executors.newCachedThreadPool());
 * EventHandler<MyEvent> handler1 = new EventHandler<MyEvent>() { ... };
 * EventHandler<MyEvent> handler2 = new EventHandler<MyEvent>() { ... };
 * dw.handleEventsWith(handler1);
 * dw.after(handler1).handleEventsWith(handler2);
 * <p/>
 * RingBuffer ringBuffer = dw.start();</code></pre>
 *
 * @param <T> the type of {@link AbstractEvent} used.
 */
public class DisruptorWizard<T extends AbstractEvent>
{
    private final RingBuffer<T> ringBuffer;
    private final Executor executor;
    private ExceptionHandler exceptionHandler;
    private EventProcessorRepository<T> eventProcessorRepository = new EventProcessorRepository<T>();
    private AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Create a new DisruptorWizard.
     *
     * @param eventFactory   the factory to create events in the ring buffer.
     * @param ringBufferSize the size of the ring buffer.
     * @param executor       an {@link Executor} to execute event processors.
     */
    public DisruptorWizard(final EventFactory<T> eventFactory, final int ringBufferSize, final Executor executor)
    {
        this(new RingBuffer<T>(eventFactory, ringBufferSize), executor);
    }

    /**
     * Create a new DisruptorWizard.
     *
     * @param eventFactory   the factory to create events in the ring buffer.
     * @param ringBufferSize the size of the ring buffer.
     * @param executor       an {@link Executor} to execute event processors.
     * @param claimStrategy  the claim strategy to use for the ring buffer.
     * @param waitStrategy   the wait strategy to use for the ring buffer.
     */
    public DisruptorWizard(final EventFactory<T> eventFactory, final int ringBufferSize, final Executor executor,
                           final ClaimStrategy.Option claimStrategy,
                           final WaitStrategy.Option waitStrategy)
    {
        this(new RingBuffer<T>(eventFactory, ringBufferSize, claimStrategy, waitStrategy), executor);
    }

    private DisruptorWizard(final RingBuffer<T> ringBuffer, final Executor executor)
    {
        this.ringBuffer = ringBuffer;
        this.executor = executor;
    }

    /**
     * Set up event handlers to handle events from the ring buffer. These handlers will process events
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
    public EventHandlerGroup<T> handleEventsWith(final EventHandler<T>... handlers)
    {
        return createEventProcessors(new EventProcessor[0], handlers);
    }

    /** Specify an exception handler to be used for any future event handlers.
     * Note that only event handlers set up after calling this method will use the exception handler.
     *
     * @param exceptionHandler the exception handler to use for any future eventprocessors.
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
    public ExceptionHandlerSetting handleExceptionsFor(final EventHandler<T> eventHandler)
    {
        return new ExceptionHandlerSetting<T>(eventHandler, eventProcessorRepository);
    }

    /** Create a group of event handlers to be used as a dependency.
     * For example if the handler <code>A</code> must process events before handler <code>B</code>:
     * <p/>
     * <pre><code>dw.after(A).handleEventsWith(B);</code></pre>
     *
     * @param handlers the event handlers, previously set up with {@link #handleEventsWith(com.lmax.disruptor.EventHandler[])},
     *                 that will form the barrier for subsequent handlers.
     * @return a {@link EventHandlerGroup} that can be used to setup a handler barrier over the specified eventprocessors.
     */
    public EventHandlerGroup<T> after(final EventHandler<T>... handlers)
    {
        EventProcessor[] selectedEventProcessors = new EventProcessor[handlers.length];
        for (int i = 0, handlersLength = handlers.length; i < handlersLength; i++)
        {
            final EventHandler<T> handler = handlers[i];
            selectedEventProcessors[i] = eventProcessorRepository.getEventProcessorFor(handler);
            if (selectedEventProcessors[i] == null)
            {
                throw new IllegalArgumentException("Event handlers must be registered before they can be used as a dependency.");
            }
        }

        return new EventHandlerGroup<T>(this, selectedEventProcessors);
    }

    /**
     * Starts the event processors and returns the fully configured ring buffer.  The ring buffer is set up to prevent overwriting any entry that is yet to
     * be processed by the slowest event processor.  This method must only be called once after all event processors have been added.
     *
     * @return the configured ring buffer.
     */
    public RingBuffer<T> start()
    {
        ringBuffer.setTrackedProcessors(eventProcessorRepository.getLastEventProcessorsInChain());
        ensureOnlyStartedOnce();
        startEventProcessors();

        return ringBuffer;
    }

    /**
     * Get the dependency barrier used by a specific handler. Note that the dependency barrier
     * may be shared by multiple event handlers.
     *
     * @param handler the handler to get the barrier for.
     * @return the DependencyBarrier used by <i>handler</i>.
     */
    public DependencyBarrier getBarrierFor(final EventHandler<T> handler)
    {
        return eventProcessorRepository.getBarrierFor(handler);
    }

    /**
     * Calls {@link com.lmax.disruptor.EventProcessor#halt()} on all of the event processors created via this wizard.
     */
    public void halt()
    {
        for (EventProcessorInfo eventprocessorInfo : eventProcessorRepository)
        {
            eventprocessorInfo.getEventProcessor().halt();
        }
    }

    EventHandlerGroup<T> createEventProcessors(final EventProcessor[] barrierEventProcessors, final EventHandler<T>[] eventHandlers)
    {
        if (started.get())
        {
            throw new IllegalStateException("All event handlers must be added before calling start.");
        }

        final EventProcessor[] createdEventProcessors = new EventProcessor[eventHandlers.length];
        final DependencyBarrier barrier = ringBuffer.newDependencyBarrier(barrierEventProcessors);
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

        eventProcessorRepository.unmarkEventProcessorsAsEndOfChain(barrierEventProcessors);
        return new EventHandlerGroup<T>(this, createdEventProcessors);
    }

    private void startEventProcessors()
    {
        for (EventProcessorInfo<T> eventProcessorInfo : eventProcessorRepository)
        {
            executor.execute(eventProcessorInfo.getEventProcessor());
        }
    }

    private void ensureOnlyStartedOnce()
    {
        if (!started.compareAndSet(false, true))
        {
            throw new IllegalStateException("DisruptorWizard.start() must only be called once.");
        }
    }
}
