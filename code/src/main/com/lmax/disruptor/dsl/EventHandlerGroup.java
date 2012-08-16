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

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.util.Util;

/**
 * A group of {@link EventProcessor}s used as part of the {@link Disruptor}.
 *
 * @param <T> the type of entry used by the event processors.
 */
public class EventHandlerGroup<T>
{
    private final Disruptor<T> disruptor;
    private final EventProcessorRepository<T> eventProcessorRepository;
    private final EventProcessor[] eventProcessors;

    EventHandlerGroup(final Disruptor<T> disruptor,
                      final EventProcessorRepository<T> eventProcessorRepository,
                      final EventProcessor[] eventProcessors)
    {
        this.disruptor = disruptor;
        this.eventProcessorRepository = eventProcessorRepository;
        this.eventProcessors = eventProcessors;
    }

    /**
     * Create a new event handler group that combines the handlers in this group with
     * <tt>handlers</tt>.
     *
     * @param handlers the handlers to combine.
     * @return a new EventHandlerGroup combining the existing and new handlers into a
     * single dependency group.
     */
    public EventHandlerGroup<T> and(final EventHandler<T>... handlers)
    {
        EventProcessor[] combinedProcessors = new EventProcessor[eventProcessors.length + handlers.length];
        for (int i = 0; i < handlers.length; i++)
        {
            combinedProcessors[i] = eventProcessorRepository.getEventProcessorFor(handlers[i]);
        }
        System.arraycopy(eventProcessors, 0, combinedProcessors, handlers.length, eventProcessors.length);

        return new EventHandlerGroup<T>(disruptor, eventProcessorRepository, combinedProcessors);
    }

    /**
     * Create a new event handler group that combines the handlers in this group with <tt>processors</tt>.
     *
     * @param processors the processors to combine.
     * @return a new EventHandlerGroup combining the existing and new processors into a single dependency group.
     */
    public EventHandlerGroup<T> and(final EventProcessor... processors)
    {
        EventProcessor[] combinedProcessors = new EventProcessor[eventProcessors.length + processors.length];

        for (EventProcessor processor : processors)
        {
            eventProcessorRepository.add(processor);
        }
        System.arraycopy(processors, 0, combinedProcessors, 0, processors.length);
        System.arraycopy(eventProcessors, 0, combinedProcessors, processors.length, eventProcessors.length);

        return new EventHandlerGroup<T>(disruptor, eventProcessorRepository, combinedProcessors);
    }

    /**
     * Set up batch handlers to consume events from the ring buffer. These handlers will only process events
     * after every {@link EventProcessor} in this group has processed the event.
     *
     * <p>This method is generally used as part of a chain. For example if the handler <code>A</code> must
     * process events before handler <code>B</code>:</p>
     *
     * <pre><code>dw.handleEventsWith(A).then(B);</code></pre>
     *
     * @param handlers the batch handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to set up a event processor barrier over the created event processors.
     */
    public EventHandlerGroup<T> then(final EventHandler<T>... handlers)
    {
        return handleEventsWith(handlers);
    }

    /**
     * Set up batch handlers to handleEventException events from the ring buffer. These handlers will only process events
     * after every {@link EventProcessor} in this group has processed the event.
     *
     * <p>This method is generally used as part of a chain. For example if the handler <code>A</code> must
     * process events before handler <code>B</code>:</p>
     *
     * <pre><code>dw.after(A).handleEventsWith(B);</code></pre>
     *
     * @param handlers the batch handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to set up a event processor barrier over the created event processors.
     */
    public EventHandlerGroup<T> handleEventsWith(final EventHandler<T>... handlers)
    {
        return disruptor.createEventProcessors(eventProcessors, handlers);
    }

    /**
     * Create a dependency barrier for the processors in this group.
     * This allows custom event processors to have dependencies on
     * {@link com.lmax.disruptor.BatchEventProcessor}s created by the disruptor.
     *
     * @return a {@link SequenceBarrier} including all the processors in this group.
     */
    public SequenceBarrier asSequenceBarrier()
    {
        return disruptor.getRingBuffer().newBarrier(Util.getSequencesFor(eventProcessors));
    }
}
