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
 * Callback handler for uncaught exceptions in the {@link AbstractEvent} processing cycle of the {@link BatchEventProcessor}
 */
public interface ExceptionHandler
{
    /**
     * Strategy for handling uncaught exceptions when processing an {@link AbstractEvent}.
     *
     * If the strategy wishes to suspend further processing by the {@link BatchEventProcessor}
     * then is should throw a {@link RuntimeException}.
     *
     * @param ex the exception that propagated from the {@link EventHandler}
     * @param currentEvent being processed when the exception occurred.
     */
    void handle(Exception ex, AbstractEvent currentEvent);
}
