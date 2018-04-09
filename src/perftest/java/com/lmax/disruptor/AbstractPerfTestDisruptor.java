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


public abstract class AbstractPerfTestDisruptor
{
    public static final int RUNS = 7;

    protected void testImplementations()
        throws Exception
    {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (getRequiredProcessorCount() > availableProcessors)
        {
            System.out.print("*** Warning ***: your system has insufficient processors to execute the test efficiently. ");
            System.out.println("Processors required = " + getRequiredProcessorCount() + " available = " + availableProcessors);
        }

        PerfTestContext[] contexts = new PerfTestContext[RUNS];

        System.out.println("Starting Disruptor tests");
        for (int i = 0; i < RUNS; i++)
        {
            System.gc();
            PerfTestContext context = runDisruptorPass();
            contexts[i] = context;
            System.out.format("Run %d, Disruptor=%,d ops/sec BatchPercent=%.2f%% AverageBatchSize=%,d\n",
                    i, context.getDisruptorOps(), context.getBatchPercent() * 100, (long)context.getAverageBatchSize());
        }
    }

    public static void printResults(final String className, final PerfTestContext[] contexts, final long[] queueOps)
    {
        for (int i = 0; i < RUNS; i++)
        {
            PerfTestContext context = contexts[i];
            System.out.format("%s run %d: BlockingQueue=%,d Disruptor=%,d ops/sec BatchPercent=%,d AverageBatchSize=%,d\n",
                              className, Integer.valueOf(i), Long.valueOf(queueOps[i]), Long.valueOf(context.getDisruptorOps()),
                              Double.valueOf(context.getBatchPercent()), Double.valueOf(context.getAverageBatchSize()));
        }
    }

    protected abstract int getRequiredProcessorCount();

    protected abstract PerfTestContext runDisruptorPass() throws Exception;
}

