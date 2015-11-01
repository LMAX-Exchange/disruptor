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
package com.lmax.disruptor.support;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;

public class EventHandlerBuilder<T>
{
    private EventHandler<T> handler = (event, seq, endOfBatch) -> {};
    private Runnable onStart = () -> {};
    private Runnable onShutdown = () -> {};

    public static <T> EventHandlerBuilder<T> aHandler()
    {
        return new EventHandlerBuilder<T>();
    }

    public EventHandlerBuilder<T> onEvent(EventHandler<T> handler)
    {
        this.handler = handler;
        return this;
    }

    public EventHandlerBuilder<T> onStart(Runnable r)
    {
        this.onStart = r;
        return this;
    }

    public EventHandlerBuilder<T> onShutdown(Runnable r)
    {
        this.onShutdown = r;
        return this;
    }

    public EventHandler<T> newInstance()
    {
        return new Handler<T>(handler, onStart, onShutdown);
    }

    private static class Handler<T> implements EventHandler<T>, LifecycleAware
    {
        private final EventHandler<T> handler;
        private final Runnable onStart;
        private final Runnable onShutdown;

        public Handler(EventHandler<T> handler, Runnable onStart, Runnable onShutdown)
        {
            this.handler = handler;
            this.onStart = onStart;
            this.onShutdown = onShutdown;
        }

        @Override
        public void onEvent(final T event, final long sequence, final boolean endOfBatch) throws Exception
        {
            handler.onEvent(event, sequence, endOfBatch);
        }

        @Override
        public void onStart()
        {
            onStart.run();
        }

        @Override
        public void onShutdown()
        {
            onShutdown.run();
        }
    }
}
