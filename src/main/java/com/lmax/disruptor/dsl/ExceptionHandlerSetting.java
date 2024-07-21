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
import com.lmax.disruptor.EventHandlerIdentity;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.ExceptionHandler;

/**
 * A support class used as part of setting an exception handler for a specific event handler.
 * For example:
 * <pre><code>disruptorWizard.handleExceptionsIn(eventHandler).with(exceptionHandler);</code></pre>
 *
 * <p>用作为特定事件处理程序设置异常处理程序的一部分的支持类。
 * 例如：<pre><code>disruptorWizard.handleExceptionsIn(eventHandler).with(exceptionHandler);</code></pre></p>
 *
 * @param <T> the type of event being handled.
 */
public class ExceptionHandlerSetting<T>
{
    private final EventHandlerIdentity handlerIdentity;
    private final ConsumerRepository consumerRepository;

    ExceptionHandlerSetting(
        final EventHandlerIdentity handlerIdentity,
        final ConsumerRepository consumerRepository)
    {
        // 一个 setting 对象，对应一个 eventHandlerIdentity
        this.handlerIdentity = handlerIdentity;
        this.consumerRepository = consumerRepository;
    }

    /**
     * Specify the {@link ExceptionHandler} to use with the event handler.
     *
     * @param exceptionHandler the exception handler to use.
     */
    @SuppressWarnings("unchecked")
    public void with(final ExceptionHandler<? super T> exceptionHandler)
    {
        // 根据成员变量 handlerIdentity 获取对应的 EventProcessor，进而设置 exceptionHandler
        final EventProcessor eventProcessor = consumerRepository.getEventProcessorFor(handlerIdentity);
        if (eventProcessor instanceof BatchEventProcessor)
        {
            ((BatchEventProcessor<T>) eventProcessor).setExceptionHandler(exceptionHandler);
            // 获取 eventHandler 对应的 sequenceBarrier，然后 alert，即触发一个异常？可能是为了唤醒等待的线程
            consumerRepository.getBarrierFor(handlerIdentity).alert();
        }
        else
        {
            throw new RuntimeException(
                "EventProcessor: " + eventProcessor + " is not a BatchEventProcessor " +
                "and does not support exception handlers");
        }
    }
}
