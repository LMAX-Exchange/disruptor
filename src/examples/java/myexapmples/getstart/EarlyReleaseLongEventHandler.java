/**
 * @(#)EarlyReleaseLongEventHandler.java, 2024/7/12
 * <p/>
 * Copyright 2022 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package myexapmples.getstart;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.Sequence;

/**
 * @author zwb
 */
public class EarlyReleaseLongEventHandler implements EventHandler<LongEvent>
{
    private Sequence sequenceCallback;

    // 定义计数器，实现在每个批次中处理多个事件，而不是逐个事件地进行处理
    // 能减少频繁更新序列号的开销，提高性能
    private int batchRemaining = 20;

    // TODO 单从 eventHandler 的角度来看，它只能看到针对当前 event 的处理；它的批量的体现是从 eventProcessor 的角度来看的

    @Override
    public void onEvent(final LongEvent event, final long sequence, final boolean endOfBatch) throws Exception
    {
        // 处理当前事件
        processEvent(event);
        // 检查是否已经完成了一个逻辑上的工作单元
        boolean logicalChunkOfWorkComplete = isLogicalChunkOfWorkComplete();
        // 当一个逻辑工作块完成时，更新序列号，通知生产者
        if (logicalChunkOfWorkComplete)
        {
            sequenceCallback.set(sequence);
        }
        // 根据是否完成了逻辑工作块或是否是批处理结束，重置或保持 batchRemaining 的值
        batchRemaining = logicalChunkOfWorkComplete || endOfBatch ? 20 : batchRemaining;
    }

    private boolean isLogicalChunkOfWorkComplete()
    {
        return --batchRemaining == 0;
    }

    @Override
    public void setSequenceCallback(final Sequence sequenceCallback)
    {
        EventHandler.super.setSequenceCallback(sequenceCallback);
        this.sequenceCallback = sequenceCallback;
    }

    private void processEvent(final LongEvent event) {
        System.out.println("Event: " + event + " Sequence: " + sequenceCallback.get() + " EndOfBatch: " + batchRemaining);
    }
}
