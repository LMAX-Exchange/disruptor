package com.lmax.disruptor;

/**
 * Implementations translate a other data representations into {@link Entry}s claimed from the {@link RingBuffer}
 *
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface EventTranslator<T extends Entry>
{
    /**
     * Translate a data representation into fields set in given {@link Entry}
     *
     * @param entry into which the data should be translated.
     * @return the resulting entry after it has been updated.
     */
    T translateTo(final T entry);
}
