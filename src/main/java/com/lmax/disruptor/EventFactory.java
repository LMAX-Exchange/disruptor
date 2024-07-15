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
 * Called by the {@link RingBuffer} to pre-populate all the events to fill the RingBuffer.
 *
 * <p>由 RingBuffer 调用来 pre-populate 所有的 event 消息以填充 RingBuffer。
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface EventFactory<T>
{
    /**
     * Implementations should instantiate an event object, with all memory already allocated where possible.
     *
     * <p>实现应该实例化一个 event 对象，尽可能地已经分配了所有的内存。
     *
     * @return T newly constructed event instance.
     */
    T newInstance();
}