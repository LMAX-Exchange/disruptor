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
package com.lmax.disruptor.util;


/**
 * Cache line padded long variable to be used when the false sharing maybe an issue.
 */
public final class PaddedLong
{
    private final int VALUE_OFFSET = 7;
    private final long[] value = new long[15];

    /**
     * Get the value as a long.
     *
     * @return the long value.
     */
    public long get()
    {
        return value[VALUE_OFFSET];
    }

    /**
     * Set the value as a long.
     *
     * @param value to be set.
     */
    public void set(final long value)
    {
        this.value[VALUE_OFFSET] = value;
    }
}
