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
package com.lmax.disruptor.dsl.stubs;

import com.lmax.disruptor.ExceptionHandler;

import java.util.concurrent.atomic.AtomicReference;

public class StubExceptionHandler implements ExceptionHandler<Object>
{
    private final AtomicReference<Throwable> exceptionHandled;

    public StubExceptionHandler(final AtomicReference<Throwable> exceptionHandled)
    {
        this.exceptionHandled = exceptionHandled;
    }

    public void handleEventException(final Throwable ex, final long sequence, final Object event)
    {
        exceptionHandled.set(ex);
    }

    @Override
    public void handleOnStartException(final Throwable ex)
    {
        exceptionHandled.set(ex);
    }

    @Override
    public void handleOnShutdownException(final Throwable ex)
    {
        exceptionHandled.set(ex);
    }
}
