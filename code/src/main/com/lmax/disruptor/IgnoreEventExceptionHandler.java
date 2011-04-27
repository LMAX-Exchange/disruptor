package com.lmax.disruptor;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience implementation of an exception handler that using standard JDK logging to log
 * the exception as {@link Level}.INFO
 */
public final class IgnoreEventExceptionHandler implements EventExceptionHandler
{
    private final static Logger LOGGER = Logger.getLogger(IgnoreEventExceptionHandler.class.getName());
    private final Logger logger;

    public IgnoreEventExceptionHandler()
    {
        this.logger = LOGGER;
    }

    public IgnoreEventExceptionHandler(final Logger logger)
    {
        this.logger = logger;
    }

    @Override
    public void handle(final Exception ex, final Entry currentEntry)
    {
        logger.log(Level.INFO, "Exception processing: " + currentEntry, ex);
    }
}
