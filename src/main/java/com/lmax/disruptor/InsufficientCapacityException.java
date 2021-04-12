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
package com.lmax.disruptor;

/**
 * Exception thrown when it is not possible to insert a value into
 * the ring buffer without it wrapping the consuming sequences.  Used
 * specifically when claiming with the {@link RingBuffer#tryNext()} call.
 *
 * <p>For efficiency this exception will not have a stack trace.
 */
@SuppressWarnings({"serial", "lgtm[java/non-sync-override]"})
public final class InsufficientCapacityException extends Exception
{
    /**
     * The efficiency saving singleton instance
     */
    public static final InsufficientCapacityException INSTANCE = new InsufficientCapacityException();

    private InsufficientCapacityException()
    {
        // Singleton
    }

    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }
}
