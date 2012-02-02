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

import java.util.*;

class EventProcessorRepository<T> implements Iterable<EventProcessorInfo<T>>
{
    private final Map<EventHandler<?>, EventProcessorInfo<T>> eventProcessorInfoByHandler = new IdentityHashMap<EventHandler<?>, EventProcessorInfo<T>>();
    private final Map<EventProcessor, EventProcessorInfo<T>> eventProcessorInfoByEventProcessor = new IdentityHashMap<EventProcessor, EventProcessorInfo<T>>();

    public void add(final EventProcessor eventprocessor,
                    final EventHandler<T> handler,
                    final SequenceBarrier barrier)
    {
        final EventProcessorInfo<T> eventProcessorInfo = new EventProcessorInfo<T>(eventprocessor, handler, barrier);
        eventProcessorInfoByHandler.put(handler, eventProcessorInfo);
        eventProcessorInfoByEventProcessor.put(eventprocessor, eventProcessorInfo);
    }

    public void add(final EventProcessor processor)
    {
        final EventProcessorInfo<T> eventProcessorInfo = new EventProcessorInfo<T>(processor, null, null);
        eventProcessorInfoByEventProcessor.put(processor, eventProcessorInfo);
    }

    public EventProcessor[] getLastEventProcessorsInChain()
    {
        List<EventProcessor> lastEventProcessors = new ArrayList<EventProcessor>();
        for (EventProcessorInfo<T> eventProcessorInfo : eventProcessorInfoByEventProcessor.values())
        {
            if (eventProcessorInfo.isEndOfChain())
            {
                lastEventProcessors.add(eventProcessorInfo.getEventProcessor());
            }
        }

        return lastEventProcessors.toArray(new EventProcessor[lastEventProcessors.size()]);
    }

    public EventProcessor getEventProcessorFor(final EventHandler<T> handler)
    {
        final EventProcessorInfo<?> eventprocessorInfo = getEventProcessorInfo(handler);
        if (eventprocessorInfo == null)
        {
            throw new IllegalArgumentException("The event handler " + handler + " is not processing events.");
        }

        return eventprocessorInfo.getEventProcessor();
    }

    public void unMarkEventProcessorsAsEndOfChain(final EventProcessor... barrierEventProcessors)
    {
        for (EventProcessor barrierEventProcessor : barrierEventProcessors)
        {
            getEventProcessorInfo(barrierEventProcessor).markAsUsedInBarrier();
        }
    }

    public Iterator<EventProcessorInfo<T>> iterator()
    {
        return eventProcessorInfoByEventProcessor.values().iterator();
    }

    public SequenceBarrier getBarrierFor(final EventHandler<T> handler)
    {
        final EventProcessorInfo<T> eventProcessorInfo = getEventProcessorInfo(handler);
        return eventProcessorInfo != null ? eventProcessorInfo.getBarrier() : null;
    }

    private EventProcessorInfo<T> getEventProcessorInfo(final EventHandler<T> handler)
    {
        return eventProcessorInfoByHandler.get(handler);
    }

    private EventProcessorInfo<T> getEventProcessorInfo(final EventProcessor barrierEventProcessor)
    {
        return eventProcessorInfoByEventProcessor.get(barrierEventProcessor);
    }
}
