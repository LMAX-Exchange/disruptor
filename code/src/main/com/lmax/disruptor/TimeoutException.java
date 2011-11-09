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

/**
 * Used to signal that an operation has timed out and been aborted.
 * <P>
 * It does not fill in a stack trace for performance reasons.
 */
@SuppressWarnings("serial")
public class TimeoutException extends Exception
{
    /** Pre-allocated exception to avoid garbage generation */
    public static final TimeoutException INSTANCE = new TimeoutException();

    /**
     * Private constructor so only a single instance exists.
     */
    private TimeoutException()
    {
    }

    /**
     * Overridden so the stack trace is not filled in for this exception for performance reasons.
     *
     * @return this instance.
     */
    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }
}
