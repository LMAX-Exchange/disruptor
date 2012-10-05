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

public interface ReferencePublisher<E>
{
    /**
     * Puts the event onto the ring buffer, will block until space is available.
     *
     * @param event to put into the ring buffer.
     */
    void put(E event);

    /**
     * Puts the event onto the ring buffer only if there is space available.
     * Return <code>false</code> if there was no space available.
     *
     * @param event to put into the ring buffer.
     * @return indicates if there was space available.
     */
    boolean offer(E event);
}