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

import com.lmax.disruptor.support.TestEvent;
import org.junit.jupiter.api.Test;

public final class IgnoreExceptionHandlerTest
{
    @Test
    public void shouldHandleAndIgnoreException()
    {
        final Exception ex = new Exception();
        final TestEvent event = new TestEvent();

        ExceptionHandler<Object> exceptionHandler = new IgnoreExceptionHandler();
        exceptionHandler.handleEventException(ex, 0L, event);
    }
}
