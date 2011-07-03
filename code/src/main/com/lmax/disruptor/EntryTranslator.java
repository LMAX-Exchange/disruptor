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
 * Implementations translate a other data representations into {@link AbstractEntry}s claimed from the {@link RingBuffer}
 *
 * @param <T> AbstractEntry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface EntryTranslator<T extends AbstractEntry>
{
    /**
     * Translate a data representation into fields set in given {@link AbstractEntry}
     *
     * @param entry into which the data should be translated.
     * @return the resulting entry after it has been updated.
     */
    T translateTo(final T entry);
}
