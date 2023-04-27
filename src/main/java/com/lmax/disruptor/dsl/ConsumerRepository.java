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
import com.lmax.disruptor.EventHandlerIdentity;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * Provides a repository mechanism to associate {@link EventHandler}s with {@link EventProcessor}s
 */
class ConsumerRepository
{
    private final Map<EventHandlerIdentity, EventProcessorInfo> eventProcessorInfoByEventHandler =
        new IdentityHashMap<>();
    private final Map<Sequence, ConsumerInfo> eventProcessorInfoBySequence =
        new IdentityHashMap<>();
    private final Collection<ConsumerInfo> consumerInfos = new ArrayList<>();

    public void add(
        final EventProcessor eventprocessor,
        final EventHandlerIdentity handlerIdentity,
        final SequenceBarrier barrier)
    {
        final EventProcessorInfo consumerInfo = new EventProcessorInfo(eventprocessor, barrier);
        eventProcessorInfoByEventHandler.put(handlerIdentity, consumerInfo);
        eventProcessorInfoBySequence.put(eventprocessor.getSequence(), consumerInfo);
        consumerInfos.add(consumerInfo);
    }

    public void add(final EventProcessor processor)
    {
        final EventProcessorInfo consumerInfo = new EventProcessorInfo(processor, null);
        eventProcessorInfoBySequence.put(processor.getSequence(), consumerInfo);
        consumerInfos.add(consumerInfo);
    }

    public void startAll(final ThreadFactory threadFactory)
    {
        consumerInfos.forEach(c -> c.start(threadFactory));
    }

    public void haltAll()
    {
        consumerInfos.forEach(ConsumerInfo::halt);
    }

    public boolean hasBacklog(final long cursor, final boolean includeStopped)
    {
        for (ConsumerInfo consumerInfo : consumerInfos)
        {
            if ((includeStopped || consumerInfo.isRunning()) && consumerInfo.isEndOfChain())
            {
                final Sequence[] sequences = consumerInfo.getSequences();
                for (Sequence sequence : sequences)
                {
                    if (cursor > sequence.get())
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public EventProcessor getEventProcessorFor(final EventHandlerIdentity handlerIdentity)
    {
        final EventProcessorInfo eventprocessorInfo = getEventProcessorInfo(handlerIdentity);
        if (eventprocessorInfo == null)
        {
            throw new IllegalArgumentException("The event handler " + handlerIdentity + " is not processing events.");
        }

        return eventprocessorInfo.getEventProcessor();
    }

    public Sequence getSequenceFor(final EventHandlerIdentity handlerIdentity)
    {
        return getEventProcessorFor(handlerIdentity).getSequence();
    }

    public void unMarkEventProcessorsAsEndOfChain(final Sequence... barrierEventProcessors)
    {
        for (Sequence barrierEventProcessor : barrierEventProcessors)
        {
            getEventProcessorInfo(barrierEventProcessor).markAsUsedInBarrier();
        }
    }

    public SequenceBarrier getBarrierFor(final EventHandlerIdentity handlerIdentity)
    {
        final ConsumerInfo consumerInfo = getEventProcessorInfo(handlerIdentity);
        return consumerInfo != null ? consumerInfo.getBarrier() : null;
    }

    private EventProcessorInfo getEventProcessorInfo(final EventHandlerIdentity handlerIdentity)
    {
        return eventProcessorInfoByEventHandler.get(handlerIdentity);
    }

    private ConsumerInfo getEventProcessorInfo(final Sequence barrierEventProcessor)
    {
        return eventProcessorInfoBySequence.get(barrierEventProcessor);
    }
}
