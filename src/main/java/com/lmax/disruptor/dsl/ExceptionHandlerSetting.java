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

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;

/**
 * A support class used as part of setting an exception handler for a specific event handler.
 * For example:
 * <pre><code>disruptorWizard.handleExceptionsIn(eventHandler).with(exceptionHandler);</code></pre>
 *
 * @param <T> the type of event being handled.
 */
public class ExceptionHandlerSetting<T>
{
    private final EventHandler<T> eventHandler;
    private final EventProcessorRepository<T> eventProcessorRepository;

    ExceptionHandlerSetting(final EventHandler<T> eventHandler,
                            final EventProcessorRepository<T> eventProcessorRepository)
    {
        this.eventHandler = eventHandler;
        this.eventProcessorRepository = eventProcessorRepository;
    }

    /**
     * Specify the {@link ExceptionHandler} to use with the event handler.
     *
     * @param exceptionHandler the exception handler to use.
     */
    public void with(ExceptionHandler exceptionHandler)
    {
        ((BatchEventProcessor<?>)eventProcessorRepository.getEventProcessorFor(eventHandler)).setExceptionHandler(exceptionHandler);
        eventProcessorRepository.getBarrierFor(eventHandler).alert();
    }
}
