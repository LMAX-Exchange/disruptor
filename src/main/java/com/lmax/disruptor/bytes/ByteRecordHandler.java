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
package com.lmax.disruptor.bytes;

/**
 * Callback for processing records as they become available in the {@link ByteRingBuffer}.
 */
@FunctionalInterface
public interface ByteRecordHandler
{
    /**
     * Called when a publisher has published a record to the {@link ByteRingBuffer}.
     *
     * @param buffer     the ring buffer's backing array
     * @param offset     of the payload in the buffer
     * @param length     of the payload in bytes
     * @param typeId     of the record, as claimed by the publisher
     * @param position   of the record being processed
     * @param endOfBatch flag to indicate if this is the last record in a batch from the {@link ByteRingBuffer}
     * @throws Exception if the ByteRecordHandler would like the exception handled further up the chain.
     */
    void onRecord(byte[] buffer, int offset, int length, int typeId, long position, boolean endOfBatch) throws Exception;
}
