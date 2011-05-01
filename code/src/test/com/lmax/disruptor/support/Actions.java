package com.lmax.disruptor.support;

import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.lib.action.CustomAction;

import java.util.concurrent.CountDownLatch;

public final class Actions
{
    public static Action countDown(final CountDownLatch latch)
    {
        return new CustomAction("Count Down Latch")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                latch.countDown();
                return null;
            }
        };
    }
}
