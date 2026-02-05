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
 * Strategy employed for producers waiting on ring buffer capacity.
 */
public interface ProducerWaitStrategy
{
    /**
     * Perform an idle action and return the updated idle counter.
     *
     * @param idleCounter current idle counter value.
     * @return updated idle counter value.
     */
    int idle(int idleCounter);

    /**
     * Reset any internal state after progress has been made.
     */
    default void reset()
    {
    }
}
