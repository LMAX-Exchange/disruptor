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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Convenience implementation of an exception handler that uses the standard JDK logging
 * of {@link System.Logger} to log the exception as {@link Level}.INFO
 *
 * <p>异常处理程序的便利实现，使用标准的JDK日志记录{@link System.Logger}将异常记录为{@link Level}.INFO</p>
 */
public final class IgnoreExceptionHandler implements ExceptionHandler<Object>
{
    private static final Logger LOGGER = System.getLogger(IgnoreExceptionHandler.class.getName());

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final Object event)
    {
        LOGGER.log(Level.INFO, () -> "Exception processing: " + sequence + " " + event, ex);
    }

    @Override
    public void handleOnStartException(final Throwable ex)
    {
        LOGGER.log(Level.INFO, "Exception during onStart()", ex);
    }

    @Override
    public void handleOnShutdownException(final Throwable ex)
    {
        LOGGER.log(Level.INFO, "Exception during onShutdown()", ex);
    }
}
