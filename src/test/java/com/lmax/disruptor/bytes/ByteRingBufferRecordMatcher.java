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

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Matches the records published from a position up to the cursor as "typeId:payload", skipping padding records.
 */
final class ByteRingBufferRecordMatcher extends TypeSafeMatcher<ByteRingBuffer>
{
    private final long fromPosition;
    private final List<String> expectedRecords;

    private ByteRingBufferRecordMatcher(final long fromPosition, final String[] expectedRecords)
    {
        this.fromPosition = fromPosition;
        this.expectedRecords = Arrays.asList(expectedRecords);
    }

    public static ByteRingBufferRecordMatcher ringBufferWithRecords(final String... expectedRecords)
    {
        return new ByteRingBufferRecordMatcher(0L, expectedRecords);
    }

    public static ByteRingBufferRecordMatcher ringBufferWithRecordsFrom(final long fromPosition, final String... expectedRecords)
    {
        return new ByteRingBufferRecordMatcher(fromPosition, expectedRecords);
    }

    @Override
    public boolean matchesSafely(final ByteRingBuffer ringBuffer)
    {
        return expectedRecords.equals(publishedRecords(ringBuffer));
    }

    @Override
    public void describeTo(final Description description)
    {
        description.appendText("ring buffer with records from position " + fromPosition + " ").appendValue(expectedRecords);
    }

    @Override
    protected void describeMismatchSafely(final ByteRingBuffer ringBuffer, final Description description)
    {
        description.appendText("was ").appendValue(publishedRecords(ringBuffer));
    }

    private List<String> publishedRecords(final ByteRingBuffer ringBuffer)
    {
        final List<String> records = new ArrayList<>();
        final int mask = ringBuffer.capacity() - 1;
        long position = fromPosition;
        while (position < ringBuffer.getCursor())
        {
            final int index = (int) position & mask;
            final long header = ByteRingBuffer.headerAt(ringBuffer.buffer(), index);
            final int recordLength = (int) header;
            final int typeId = (int) (header >>> 32);
            if (typeId != ByteRingBuffer.PADDING_TYPE_ID)
            {
                final String payload = new String(
                    ringBuffer.buffer(),
                    index + ByteRingBuffer.HEADER_LENGTH,
                    recordLength - ByteRingBuffer.HEADER_LENGTH,
                    StandardCharsets.US_ASCII);
                records.add(typeId + ":" + payload);
            }
            position += ByteRingBuffer.align(recordLength);
        }
        return records;
    }
}
