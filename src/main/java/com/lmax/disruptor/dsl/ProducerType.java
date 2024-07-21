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
package com.lmax.disruptor.dsl;

/**
 * Defines producer types to support creation of RingBuffer with correct sequencer and publisher.
 *
 * <p>定义生产者类型，以支持使用正确的序列器和发布者创建 RingBuffer。</p>
 */
public enum ProducerType
{
    /**
     * Create a RingBuffer with a single event publisher to the RingBuffer
     *
     * <p>创建一个RingBuffer，其中只有一个事件发布者发布到 RingBuffer</p>
     */
    SINGLE,

    /**
     * Create a RingBuffer supporting multiple event publishers to the one RingBuffer
     *
     * <p>创建一个支持多个事件发布者发布到一个 RingBuffer 的 RingBuffer</p>
     */
    MULTI
}
