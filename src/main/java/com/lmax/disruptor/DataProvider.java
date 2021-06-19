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
 * Typically used to decouple classes from {@link RingBuffer} to allow easier testing
 *
 * @param <T> The type provided by the implementation
 */
public interface DataProvider<T>
{
    /**
     * @param sequence The sequence at which to find the data
     * @return the data item located at that sequence
     */
    T get(long sequence);
}
