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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience implementation of an exception handler that using standard JDK logging to log
 * the exception as {@link Level}.SEVERE and re-throw it wrapped in a {@link RuntimeException}
 */
public final class FatalExceptionHandler implements ExceptionHandler<Object>
{
    private static final Logger LOGGER = Logger.getLogger(FatalExceptionHandler.class.getName());
    private final Logger logger;

    public FatalExceptionHandler()
    {
        this.logger = LOGGER;
    }

    public FatalExceptionHandler(final Logger logger)
    {
        this.logger = logger;
    }

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final Object event)
    {
        logger.log(Level.SEVERE, "Exception processing: " + sequence + " " + event, ex);

        throw new RuntimeException(ex);
    }

    @Override
    public void handleOnStartException(final Throwable ex)
    {
        logger.log(Level.SEVERE, "Exception during onStart()", ex);
    }

    @Override
    public void handleOnShutdownException(final Throwable ex)
    {
        logger.log(Level.SEVERE, "Exception during onShutdown()", ex);
    }
}
