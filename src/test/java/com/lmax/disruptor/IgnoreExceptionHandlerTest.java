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

import com.lmax.disruptor.support.TestEvent;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Rule;
import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class IgnoreExceptionHandlerTest
{
    @Rule
    public final JUnitRuleMockery context = new JUnitRuleMockery();

    public IgnoreExceptionHandlerTest()
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);
    }

    @Test
    public void shouldHandleAndIgnoreException()
    {
        final Exception ex = new Exception();
        final TestEvent event = new TestEvent();

        final Logger logger = context.mock(Logger.class);

        context.checking(
            new Expectations()
            {
                {
                    oneOf(logger).log(Level.INFO, "Exception processing: 0 " + event, ex);
                }
            });

        ExceptionHandler<Object> exceptionHandler = new IgnoreExceptionHandler(logger);
        exceptionHandler.handleEventException(ex, 0L, event);
    }
}
