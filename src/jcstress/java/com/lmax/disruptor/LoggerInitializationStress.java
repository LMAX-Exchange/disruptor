package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.Util;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Mode;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.Signal;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.logging.LogManager;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/**
 * Validate that creating a {@link Disruptor} instance with a custom {@link ExceptionHandler} does not
 * initialize the logging framework. Using JCStress is a stretch, we're not validating a race condition,
 * rather that the logging framework is not started. This type of test doesn't work well in unit tests
 * because it requires JVM isolation, and any interactions with a logger invalidate the test.
 */
@JCStressTest(Mode.Termination)
@Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "Logger has not been initialized")
@Outcome(
        id = {"STALE", "ERROR"},
        expect = FORBIDDEN,
        desc = "Logger has been initialized. This has the potential to cause " +
                "deadlocks when disruptor is used within logging frameworks")
public class LoggerInitializationStress
{
    private static volatile boolean logManagerInitialized;
    private static volatile boolean disruptorInitialized;
    static
    {
        System.setProperty("java.util.logging.manager", DisruptorLogManager.class.getCanonicalName());
    }

    @Actor
    public void actor() throws Exception
    {
        // Wait for a the Disruptor instance to be initialized
        while (!disruptorInitialized)
        {
        }
        // Validate state
        if (logManagerInitialized)
        {
            throw new RuntimeException("Expected the LogManager to be uninitialized");
        }
        // Use Unsafe to determine if the LogManager has been initialized.
        // Accessing the LogManager normally using LogManager.getLogManager
        // results in initialization which modifies static state and prevents
        // subsequent runs from executing successfully.
        Field managerField = LogManager.class.getDeclaredField("manager");
        Unsafe unsafe = Util.getUnsafe();
        Object managerBase = unsafe.staticFieldBase(managerField);
        long managerOffset = unsafe.staticFieldOffset(managerField);
        Object logManager = unsafe.getObject(managerBase, managerOffset);
        if (logManager != null)
        {
            throw new RuntimeException("Unexpected LogManager: " + logManager);
        }
    }

    @Signal
    public void signal()
    {
        final Disruptor disruptor = new Disruptor<>(
                SimpleEvent::new,
                128,
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new BlockingWaitStrategy());
        disruptor.setDefaultExceptionHandler(SimpleEventExceptionHandler.INSTANCE);
        disruptor.handleEventsWith(SimpleEventHandler.INSTANCE);
        disruptor.start();
        disruptor.halt();
        disruptorInitialized = true;
    }

    public static final class DisruptorLogManager extends LogManager
    {
        static
        {
            logManagerInitialized = true;
        }
    }

    public static final class SimpleEvent
    {
        private long value = Long.MIN_VALUE;

        public long getValue()
        {
            return value;
        }

        public void setValue(final long value)
        {
            this.value = value;
        }

        @Override
        public String toString()
        {
            return "SimpleEvent{" +
                    "value=" + value +
                    '}';
        }
    }

    public enum SimpleEventHandler implements EventHandler<SimpleEvent>
    {
        INSTANCE;

        @Override
        public void onEvent(final SimpleEvent event, final long sequence, final boolean endOfBatch)
        {
            // nop
        }
    }

    public enum SimpleEventExceptionHandler implements ExceptionHandler<SimpleEvent>
    {
        INSTANCE;

        @Override
        public void handleEventException(final Throwable ex, final long sequence, final SimpleEvent event)
        {
            // nop
        }

        @Override
        public void handleOnStartException(final Throwable ex)
        {
            // nop
        }

        @Override
        public void handleOnShutdownException(final Throwable ex)
        {
            // nop
        }
    }

}
