/*
 * Copyright 2012 LMAX Ltd.
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
package com.lmax.disruptor.support;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class WaitStrategyTestUtil
{
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public static void assertWaitForWithDelayOf(long sleepTimeMillis, WaitStrategy waitStrategy)
        throws InterruptedException, BrokenBarrierException, AlertException, TimeoutException
    {
        SequenceUpdater sequenceUpdater = new SequenceUpdater(sleepTimeMillis, waitStrategy);
        EXECUTOR.execute(sequenceUpdater);
        sequenceUpdater.waitForStartup();
        Sequence cursor = new Sequence(0);
        long sequence = waitStrategy.waitFor(0, cursor, sequenceUpdater.sequence, new DummySequenceBarrier());

        assertThat(sequence, is(0L));
    }
}
