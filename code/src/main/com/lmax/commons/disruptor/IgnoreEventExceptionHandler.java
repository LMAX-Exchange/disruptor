package com.lmax.commons.disruptor;

import java.util.logging.Level;
import java.util.logging.Logger;

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
