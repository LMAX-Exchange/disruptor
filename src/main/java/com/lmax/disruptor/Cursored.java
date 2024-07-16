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
 * Implementors of this interface must provide a single long value
 * that represents their current cursor value.  Used during dynamic
 * add/remove of Sequences from a
 * {@link SequenceGroups#addSequences(Object, java.util.concurrent.atomic.AtomicReferenceFieldUpdater, Cursored, Sequence...)}.
 *
 * <p>当前接口的实现类必须提供一个表示当前游标值的单个长整型值。
 * 在动态添加/删除Sequences时使用，从{@link SequenceGroups#addSequences(Object, java.util.concurrent.atomic.AtomicReferenceFieldUpdater, Cursored, Sequence...)}。</p>
 */
public interface Cursored
{
    /**
     * Get the current cursor value.
     *
     * <p>获取当前游标值。</p>
     *
     * @return current cursor value
     */
    long getCursor();
}
