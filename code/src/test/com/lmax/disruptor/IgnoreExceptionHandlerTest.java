package com.lmax.disruptor;

import com.lmax.disruptor.support.TestEntry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.logging.Level;
import java.util.logging.Logger;

@RunWith(JMock.class)
public final class IgnoreExceptionHandlerTest
{
    private final Mockery context = new Mockery();

    public IgnoreExceptionHandlerTest()
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);
    }

    @Test
    public void shouldHandleAndIgnoreException()
    {
        final Exception ex = new Exception();
        final Entry entry = new TestEntry();

        final Logger logger = context.mock(Logger.class);

        context.checking(new Expectations()
        {
            {
                oneOf(logger).log(Level.INFO, "Exception processing: " + entry, ex);
            }
        });

        ExceptionHandler exceptionHandler = new IgnoreExceptionHandler(logger);
        exceptionHandler.handle(ex, entry);
    }
}
