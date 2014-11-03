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

public interface Backchannel
{
    /**
     * Checks whether this Backchannel needs to be processed.
     *
     * @return true if there is work to be performed
     */
    boolean shouldProcess();

    /**
     * Processes whatever work is in this Backchannel. Can be called regardless
     * of the result of shouldProcess(). This is called at well defined times
     * (e.g., at the end of a batch by the BatchEventProcessor).
     */
    void process();

    /**
     * Registers a WaitStrategy that producers should notify when adding
     * work to this Backchannel.
     *
     * @param waitStrategy the WaitStrategy to notify
     */
    void prepareWait(WaitStrategy waitStrategy);

    void waitDone();

    Backchannel NONE = new Backchannel() {

        @Override
        public boolean shouldProcess()
        {
            return false;
        }

        @Override
        public void process()
        {
        }

        @Override
        public void prepareWait(WaitStrategy waitStrategy)
        {
        }

        @Override
        public void waitDone()
        {
        }
    };
}
