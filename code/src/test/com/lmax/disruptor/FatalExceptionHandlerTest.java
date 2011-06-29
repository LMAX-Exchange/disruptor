package com.lmax.disruptor;

import com.lmax.disruptor.support.TestEntry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.logging.Level;
import java.util.logging.Logger;

@RunWith(JMock.class)
public final class FatalExceptionHandlerTest
{
    private final Mockery context = new Mockery();

    public FatalExceptionHandlerTest()
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);
    }

    @Test
    public void shouldHandleFatalException()
    {
        final Exception causeException = new Exception();
        final AbstractEntry entry = new TestEntry();

        final Logger logger = context.mock(Logger.class);

        context.checking(new Expectations()
        {
            {
                oneOf(logger).log(Level.SEVERE, "Exception processing: " + entry, causeException);
            }
        });

        ExceptionHandler exceptionHandler = new FatalExceptionHandler(logger);

        try
        {
            exceptionHandler.handle(causeException, entry);
        }
        catch (RuntimeException ex)
        {
            Assert.assertEquals(causeException, ex.getCause());
        }
    }
}
