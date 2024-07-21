/*
 * Copyright 2021 LMAX Ltd.
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
 * Provides static methods for accessing a default {@link ExceptionHandler} object.
 *
 * <p>提供用于访问默认{@link ExceptionHandler}对象的静态方法。</p>
 */
public final class ExceptionHandlers
{

    /**
     * Get a reference to the default {@link ExceptionHandler} instance.
     *
     * <p>获取对默认{@link ExceptionHandler}实例的引用。</p>
     *
     * @return a reference to the default {@link ExceptionHandler} instance
     */
    public static ExceptionHandler<Object> defaultHandler()
    {
        return DefaultExceptionHandlerHolder.HANDLER;
    }

    private ExceptionHandlers()
    {
    }

    // lazily initialize the default exception handler.
    // This nested object isn't strictly necessary unless additional utility functionality is
    // added to ExceptionHandlers, but it exists to ensure the code remains obvious.
    // 懒初始化默认异常处理程序。
    // 除非将其他实用程序功能添加到ExceptionHandlers，否则此嵌套对象并不是绝对必要的，但它确保代码保持明显。
    private static final class DefaultExceptionHandlerHolder
    {
        private static final ExceptionHandler<Object> HANDLER = new FatalExceptionHandler();
    }
}
