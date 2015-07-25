package com.lmax.disruptor;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static org.hamcrest.CoreMatchers.is;

final class RingBufferEventMatcher extends TypeSafeMatcher<RingBuffer<Object[]>>
{
    private final Matcher<?>[] expectedValueMatchers;

    private RingBufferEventMatcher(final Matcher<?>[] expectedValueMatchers)
    {
        this.expectedValueMatchers = expectedValueMatchers;
    }

    @Factory
    public static RingBufferEventMatcher ringBufferWithEvents(final Matcher<?>... valueMatchers)
    {
        return new RingBufferEventMatcher(valueMatchers);
    }

    @Factory
    public static RingBufferEventMatcher ringBufferWithEvents(final Object... values)
    {
        Matcher<?>[] valueMatchers = new Matcher[values.length];
        for (int i = 0; i < values.length; i++)
        {
            final Object value = values[i];
            valueMatchers[i] = is(value);
        }
        return new RingBufferEventMatcher(valueMatchers);
    }

    @Override
    public boolean matchesSafely(final RingBuffer<Object[]> ringBuffer)
    {
        boolean matches = true;
        for (int i = 0; i < expectedValueMatchers.length; i++)
        {
            final Matcher<?> expectedValueMatcher = expectedValueMatchers[i];
            matches &= expectedValueMatcher.matches(ringBuffer.get(i)[0]);
        }
        return matches;
    }

    @Override
    public void describeTo(final Description description)
    {
        description.appendText("Expected ring buffer with events matching: ");

        for (Matcher<?> expectedValueMatcher : expectedValueMatchers)
        {
            expectedValueMatcher.describeTo(description);
        }
    }
}