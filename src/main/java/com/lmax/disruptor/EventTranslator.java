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
 * Implementations translate (write) data representations into events claimed from the {@link RingBuffer}.
 *
 * <p>实现将数据表示转换（写入）为从{@link RingBuffer}中声明的事件。</p>
 *
 * <p>When publishing to the RingBuffer, provide an EventTranslator. The RingBuffer will select the next available
 * event by sequence and provide it to the EventTranslator (which should update the event), before publishing
 * the sequence update.
 *
 * <p>在发布到RingBuffer时，提供一个EventTranslator。
 * RingBuffer将通过序列选择下一个可用的事件，并将其提供给EventTranslator（应该更新事件），然后发布序列更新。</p>
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface EventTranslator<T>
{
    /**
     * Translate a data representation into fields set in given event
     *
     * @param event    into which the data should be translated.
     * @param sequence that is assigned to event.
     */
    void translateTo(T event, long sequence);
}
