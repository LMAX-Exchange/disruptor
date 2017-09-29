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
package com.lmax.disruptor;

/**
 * An aggregate collection of {@link EventHandler}s that get called in sequence for each event.
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class AggregateEventHandler<T>
    implements EventHandler<T>, LifecycleAware
{
    private final EventHandler<T>[] eventHandlers;

    /**
     * Construct an aggregate collection of {@link EventHandler}s to be called in sequence.
     *
     * @param eventHandlers to be called in sequence.
     */
    @SafeVarargs
    public AggregateEventHandler(final EventHandler<T>... eventHandlers)
    {
        this.eventHandlers = eventHandlers;
    }

    @Override
    public void onEvent(final T event, final long sequence, final boolean endOfBatch)
        throws Exception
    {
        for (final EventHandler<T> eventHandler : eventHandlers)
        {
            eventHandler.onEvent(event, sequence, endOfBatch);
        }
    }

    @Override
    public void onStart()
    {
        for (final EventHandler<T> eventHandler : eventHandlers)
        {
            if (eventHandler instanceof LifecycleAware)
            {
                ((LifecycleAware) eventHandler).onStart();
            }
        }
    }

    @Override
    public void onShutdown()
    {
        for (final EventHandler<T> eventHandler : eventHandlers)
        {
            if (eventHandler instanceof LifecycleAware)
            {
                ((LifecycleAware) eventHandler).onShutdown();
            }
        }
    }
}
