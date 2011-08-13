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

import com.lmax.disruptor.AbstractEvent;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.DependencyBarrier;

import java.util.*;

class EventProcessorRepository<T extends AbstractEvent> implements Iterable<EventProcessorInfo<T>>
{
    private final Map<EventHandler, EventProcessorInfo<T>> eventprocessorInfoByHandler = new IdentityHashMap<EventHandler, EventProcessorInfo<T>>();
    private final Map<EventProcessor, EventProcessorInfo<T>> eventprocessorInfoByEventProcessor = new IdentityHashMap<EventProcessor, EventProcessorInfo<T>>();

    public void add(EventProcessor eventprocessor, EventHandler<T> handler, final DependencyBarrier barrier)
    {
        final EventProcessorInfo<T> eventprocessorInfo = new EventProcessorInfo<T>(eventprocessor, handler, barrier);
        eventprocessorInfoByHandler.put(handler, eventprocessorInfo);
        eventprocessorInfoByEventProcessor.put(eventprocessor, eventprocessorInfo);
    }

    public EventProcessor[] getLastEventProcessorsInChain()
    {
        List<EventProcessor> lastEventProcessors = new ArrayList<EventProcessor>();
        for (EventProcessorInfo<T> eventprocessorInfo : eventprocessorInfoByHandler.values())
        {
            if (eventprocessorInfo.isEndOfChain())
            {
                lastEventProcessors.add(eventprocessorInfo.getEventProcessor());
            }
        }
        return lastEventProcessors.toArray(new EventProcessor[lastEventProcessors.size()]);
    }

    public EventProcessor getEventProcessorFor(final EventHandler<T> handler)
    {
        final EventProcessorInfo eventprocessorInfo = getEventProcessorInfo(handler);
        return eventprocessorInfo != null ? eventprocessorInfo.getEventProcessor() : null;
    }

    public void unmarkEventProcessorsAsEndOfChain(final EventProcessor... barrierEventProcessors)
    {
        for (EventProcessor barrierEventProcessor : barrierEventProcessors)
        {
            getEventProcessorInfo(barrierEventProcessor).usedInBarrier();
        }
    }

    public Iterator<EventProcessorInfo<T>> iterator()
    {
        return eventprocessorInfoByHandler.values().iterator();
    }

    public DependencyBarrier getBarrierFor(final EventHandler<T> handler)
    {
        final EventProcessorInfo<T> eventprocessorInfo = getEventProcessorInfo(handler);
        return eventprocessorInfo != null ? eventprocessorInfo.getBarrier() : null;
    }

    private EventProcessorInfo<T> getEventProcessorInfo(final EventHandler<T> handler)
    {
        return eventprocessorInfoByHandler.get(handler);
    }

    private EventProcessorInfo<T> getEventProcessorInfo(final EventProcessor barrierEventProcessor)
    {
        return eventprocessorInfoByEventProcessor.get(barrierEventProcessor);
    }
}
