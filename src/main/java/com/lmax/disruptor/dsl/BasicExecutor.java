package com.lmax.disruptor.dsl;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class BasicExecutor implements Executor
{
    private final ThreadFactory factory;
    //这是一个同步线程组
    private final Queue<Thread> threads = new ConcurrentLinkedQueue<Thread>();

    public BasicExecutor(ThreadFactory factory)
    {
        this.factory = factory;
    }

    @Override
    public void execute(Runnable command)
    {
        //执行的时候，先创建线程
        final Thread thread = factory.newThread(command);
        if (null == thread)
        {
            throw new RuntimeException("Failed to create thread to run: " + command);
        }
        //启动线程
        thread.start();
        //加入线程组
        threads.add(thread);
    }

    @Override
    public String toString()
    {
        return "BasicExecutor{" +
            "threads=" + dumpThreadInfo() +
            '}';
    }

    private String dumpThreadInfo()
    {
        final StringBuilder sb = new StringBuilder();

        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        for (Thread t : threads)
        {
            ThreadInfo threadInfo = threadMXBean.getThreadInfo(t.getId());
            sb.append("{");
            sb.append("name=").append(t.getName()).append(",");
            sb.append("id=").append(t.getId()).append(",");
            sb.append("state=").append(threadInfo.getThreadState()).append(",");
            sb.append("lockInfo=").append(threadInfo.getLockInfo());
            sb.append("}");
        }

        return sb.toString();
    }
}
