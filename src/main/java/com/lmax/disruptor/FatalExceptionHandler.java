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
 * of {@link System.Logger} to log the exception as {@link Level}.ERROR and re-throw
 * it wrapped in a {@link RuntimeException}
 */
public final class FatalExceptionHandler implements ExceptionHandler<Object>
{
    private static final Logger LOGGER = System.getLogger(FatalExceptionHandler.class.getName());

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final Object event)
    {
        LOGGER.log(Level.ERROR, () -> "Exception processing: " + sequence + " " + event, ex);

        throw new RuntimeException(ex);
    }

    @Override
    public void handleOnStartException(final Throwable ex)
    {
        LOGGER.log(Level.ERROR, "Exception during onStart()", ex);
    }

    @Override
    public void handleOnShutdownException(final Throwable ex)
    {
        LOGGER.log(Level.ERROR, "Exception during onShutdown()", ex);
    }
}
