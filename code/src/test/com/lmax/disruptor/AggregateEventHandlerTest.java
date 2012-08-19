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

import org.jmock.*;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
@SuppressWarnings("unchecked")
public final class AggregateEventHandlerTest
{
    private Mockery context = new Mockery()
    {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    private final org.jmock.Sequence callSequence = context.sequence("callSequence");

    private final LifecycleAwareEventHandler<int[]> eh1 = context.mock(LifecycleAwareEventHandler.class, "eh1");
    private final LifecycleAwareEventHandler<int[]> eh2 = context.mock(LifecycleAwareEventHandler.class, "eh2");
    private final LifecycleAwareEventHandler<int[]> eh3 = context.mock(LifecycleAwareEventHandler.class, "eh3");

    @Test
    public void shouldCallOnEventInSequence()
        throws Exception
    {
        final int[] event = {7};
        final long sequence = 3L;
        final boolean endOfBatch = true;

        context.checking(new Expectations()
        {
            {
                oneOf(eh1).onEvent(event, sequence, endOfBatch);
                inSequence(callSequence);

                oneOf(eh2).onEvent(event, sequence, endOfBatch);
                inSequence(callSequence);

                oneOf(eh3).onEvent(event, sequence, endOfBatch);
                inSequence(callSequence);
            }
        });

        final AggregateEventHandler<int[]> aggregateEventHandler = new AggregateEventHandler<int[]>(eh1, eh2, eh3);

        aggregateEventHandler.onEvent(event, sequence, endOfBatch);
    }

    @Test
    public void shouldCallOnStartInSequence()
        throws Exception
    {
        context.checking(new Expectations()
        {
            {
                oneOf(eh1).onStart();
                inSequence(callSequence);

                oneOf(eh2).onStart();
                inSequence(callSequence);

                oneOf(eh3).onStart();
                inSequence(callSequence);
            }
        });

        final AggregateEventHandler<int[]> aggregateEventHandler = new AggregateEventHandler<int[]>(eh1, eh2, eh3);

        aggregateEventHandler.onStart();
    }

    @Test
    public void shouldCallOnShutdownInSequence()
        throws Exception
    {
        context.checking(new Expectations()
        {
            {
                oneOf(eh1).onShutdown();
                inSequence(callSequence);

                oneOf(eh2).onShutdown();
                inSequence(callSequence);

                oneOf(eh3).onShutdown();
                inSequence(callSequence);
            }
        });

        final AggregateEventHandler<int[]> aggregateEventHandler = new AggregateEventHandler<int[]>(eh1, eh2, eh3);

        aggregateEventHandler.onShutdown();
    }

    @Test
    public void shouldHandleEmptyListOfEventHandlers() throws Exception
    {
        final AggregateEventHandler<int[]> aggregateEventHandler = new AggregateEventHandler<int[]>();

        aggregateEventHandler.onEvent(new int[]{7}, 0L, true);
        aggregateEventHandler.onStart();
        aggregateEventHandler.onShutdown();
    }

    public static class LifecycleAwareEventHandler<T>
        implements EventHandler<T>, LifecycleAware
    {
        @Override
        public void onEvent(final T event, final long sequence, final boolean endOfBatch) throws Exception
        {
        }

        @Override
        public void onStart()
        {
        }

        @Override
        public void onShutdown()
        {
        }
    }
}
