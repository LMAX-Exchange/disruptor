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
 * The Publisher interface allows different strategies for tracking the slots in the RingBuffer
 */
interface Publisher
{
	/**
	 * Publishes a sequence to the buffer. Call when the event has been filled.
	 *
	 * @param sequence 
	 */
    void publish(long sequence);

    /**
     * Confirms if a sequence is published and the event is available for use; non-blocking.
     *
     * @param sequence of the buffer to check
     * @return true if the sequence is available for use, false if not
     */
    boolean isAvailable(long sequence);

    /**
     * Ensure a given sequence has been published and the event is now available.<p/>
     *
     * Blocks if the sequence is not available yet.
     *
     * @param sequence of the event to wait for
     */
    void ensureAvailable(long sequence);

}
