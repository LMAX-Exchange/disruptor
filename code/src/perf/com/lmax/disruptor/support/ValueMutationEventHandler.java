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
package com.lmax.disruptor.support;

import com.lmax.disruptor.EventHandler;

public final class ValueMutationEventHandler implements EventHandler<ValueEvent>
{
    private final Operation operation;
    private final long[] value = new long[15]; // cache line padded

    public ValueMutationEventHandler(final Operation operation)
    {
        this.operation = operation;
    }

    public long getValue()
    {
        return value[7];
    }

    public void reset()
    {
        value[7] = 0L;
    }

    @Override
    public void onEvent(final ValueEvent event, final long sequence, final boolean endOfBatch) throws Exception
    {
        value[7] = operation.op(value[7], event.getValue());
    }
}
