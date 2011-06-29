package com.lmax.disruptor;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience implementation of an exception handler that using standard JDK logging to log
 * the exception as {@link Level}.SEVERE and re-throw it wrapped in a {@link RuntimeException}
 */
public final class FatalExceptionHandler implements ExceptionHandler
{
    private final static Logger LOGGER = Logger.getLogger(FatalExceptionHandler.class.getName());
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
    public void handle(final Exception ex, final AbstractEntry currentEntry)
    {
        logger.log(Level.SEVERE, "Exception processing: " + currentEntry, ex);

        throw new RuntimeException(ex);
    }
}
