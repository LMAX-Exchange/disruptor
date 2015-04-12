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
import org.junit.Assert;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StubExecutor implements Executor
{
    private final DaemonThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;
    private final Collection<Thread> threads = new CopyOnWriteArrayList<Thread>();
    private final AtomicBoolean ignoreExecutions = new AtomicBoolean(false);
    private final AtomicInteger executionCount = new AtomicInteger(0);

    public void execute(final Runnable command)
    {
        executionCount.getAndIncrement();
        if (!ignoreExecutions.get())
        {
            Thread t = threadFactory.newThread(command);
            t.setName(command.toString());
            threads.add(t);
            t.start();
        }
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

            Assert.assertFalse("Failed to stop thread: " + thread, thread.isAlive());
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
}
