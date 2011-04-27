package com.lmax.disruptor;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience implementation of an exception handler that using standard JDK logging to log
 * the exception as {@link Level}.SEVERE and re-throw it wrapped in a {@link RuntimeException}
 */
public final class FatalEventExceptionHandler implements EventExceptionHandler
{
    private final static Logger LOGGER = Logger.getLogger(FatalEventExceptionHandler.class.getName());
    private final Logger logger;

    public FatalEventExceptionHandler()
    {
        this.logger = LOGGER;
    }

    public FatalEventExceptionHandler(final Logger logger)
    {
        this.logger = logger;
    }

    @Override
    public void handle(final Exception ex, final Entry currentEntry)
    {
        logger.log(Level.SEVERE, "Exception processing: " + currentEntry, ex);

        throw new RuntimeException(ex);
    }
}
