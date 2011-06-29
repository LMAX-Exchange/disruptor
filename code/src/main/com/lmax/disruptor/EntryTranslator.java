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
