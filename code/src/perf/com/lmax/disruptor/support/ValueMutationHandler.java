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

import com.lmax.disruptor.BatchHandler;

public final class ValueMutationHandler implements BatchHandler<ValueEntry>
{
    private final Operation operation;
    private long value;

    public ValueMutationHandler(final Operation operation)
    {
        this.operation = operation;
    }

    public long getValue()
    {
        return value;
    }

    public void reset()
    {
        value = 0L;
    }

    @Override
    public void onAvailable(final ValueEntry entry) throws Exception
    {
        value = operation.op(value, entry.getValue());
    }

    @Override
    public void onEndOfBatch() throws Exception
    {
    }
}
