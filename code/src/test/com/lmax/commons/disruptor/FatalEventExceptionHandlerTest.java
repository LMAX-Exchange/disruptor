package com.lmax.commons.disruptor;

import com.lmax.commons.disruptor.support.TestEntry;
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
public final class FatalEventExceptionHandlerTest
{
    private final Mockery context = new Mockery();

    public FatalEventExceptionHandlerTest()
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);
    }

    @Test
    public void shouldHandleFatalException()
    {
        final Exception causeException = new Exception();
        final Entry entry = new TestEntry();

        final Logger logger = context.mock(Logger.class);

        context.checking(new Expectations()
        {
            {
                oneOf(logger).log(Level.SEVERE, "Exception processing: " + entry, causeException);
            }
        });

        EventExceptionHandler eventExceptionHandler = new FatalEventExceptionHandler(logger);

        try
        {
            eventExceptionHandler.handle(causeException, entry);
        }
        catch (RuntimeException ex)
        {
            Assert.assertEquals(causeException, ex.getCause());
        }
    }
}
