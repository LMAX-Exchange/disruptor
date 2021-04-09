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
package com.lmax.disruptor.dsl.stubs;

import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;

public final class StubThreadFactory implements ThreadFactory, AfterEachCallback
{
    private static final Logger LOGGER = Logger.getLogger(StubThreadFactory.class.getName());

    private final DaemonThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;
    private final Collection<Thread> threads = new CopyOnWriteArrayList<>();
    private final AtomicBoolean ignoreExecutions = new AtomicBoolean(false);
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final List<Throwable> threadErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<IgnoredException> ignoredExceptions = new ArrayList<>();

    @Override
    public Thread newThread(final Runnable command)
    {
        executionCount.getAndIncrement();
        Runnable toExecute = () ->
        {
            try
            {
                command.run();
            }
            catch (Throwable t)
            {
                threadErrors.add(t);
            }
        };
        if (ignoreExecutions.get())
        {
            toExecute = new NoOpRunnable();
        }
        final Thread thread = threadFactory.newThread(toExecute);
        thread.setName(command.toString());
        threads.add(thread);
        return thread;
    }

    public void joinAllThreads()
    {
        for (Thread thread : threads)
        {
            if (thread.isAlive())
            {
                try
                {
                    thread.interrupt();
                    thread.join(5000);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            assertFalse(thread.isAlive(), "Failed to stop thread: " + thread);
        }

        threads.clear();
    }

    public void ignoreExecutions()
    {
        ignoreExecutions.set(true);
    }

    public int getExecutionCount()
    {
        return executionCount.get();
    }

    @Override
    public void afterEach(final ExtensionContext context) throws Exception
    {
        if (!threadErrors.isEmpty())
        {
            for (final Throwable threadError : threadErrors)
            {
                boolean ignored = false;
                for (final IgnoredException ignoredException : ignoredExceptions)
                {
                    if (threadError.getMessage().equalsIgnoreCase(ignoredException.exceptionMessage))
                    {
                        LOGGER.info("Ignoring '" + threadError.getMessage() + "' " +
                                "because: " + ignoredException.reason);
                        ignored = true;
                        break;
                    }
                }
                if (!ignored)
                {
                    throw new Exception(threadError);
                }
            }
        }
    }

    private static final class IgnoredException
    {
        final String exceptionMessage;
        final String reason;

        IgnoredException(final String exceptionMessage, final String reason)
        {
            this.exceptionMessage = exceptionMessage;
            this.reason = reason;
        }
    }

    private static final class NoOpRunnable implements Runnable
    {
        @Override
        public void run()
        {
        }
    }
}
