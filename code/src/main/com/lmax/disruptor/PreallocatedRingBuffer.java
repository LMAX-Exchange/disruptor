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
 * Ring based store of reusable entries containing the data representing 
 * an event being exchanged between event publisher and {@link EventProcessor}s.
 *
 * @param <E> implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class PreallocatedRingBuffer<E> extends RingBuffer<E>
{
    protected final Object[] entries;
    
    /**
     * Construct a RingBuffer with the full option set.
     *
     * @param eventFactory to newInstance entries for filling the RingBuffer
     * @param sequencer sequencer to handle the ordering of events moving through the RingBuffer.
     *
     * @throws IllegalArgumentException if bufferSize is not a power of 2
     */
    public PreallocatedRingBuffer(final EventFactory<E> eventFactory, Sequencer sequencer)
    {
        super(sequencer);
        entries = new Object[sequencer.getBufferSize()];
        fill(eventFactory);
    }

    /**
     * Construct a RingBuffer with default implementations of:
     * {@link MultiProducerSequencer} and {@link BlockingWaitStrategy}
     *
     * @param eventFactory to newInstance entries for filling the RingBuffer
     * @param bufferSize of the RingBuffer that will be rounded up to the next power of 2
     */
    public PreallocatedRingBuffer(final EventFactory<E> eventFactory, final int bufferSize)
    {
        this(eventFactory,
             new MultiProducerSequencer(bufferSize, new BlockingWaitStrategy()));
    }

    /**
     * Get the event for a given sequence in the RingBuffer.
     *
     * @param sequence for the event
     * @return event for the sequence
     */
    @SuppressWarnings("unchecked")
    public E get(final long sequence)
    {
        sequencer.ensureAvailable(sequence);
        return (E)entries[(int)sequence & indexMask];
    }

    /**
     * Publishes an event to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialized) 
     * event from the ring buffer and publishing the claimed sequence
     * after translation.
     * 
     * @param translator The user specified translation for the event
     */
    public void publishEvent(final EventTranslator<E> translator)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence);
    }
    
    /**
     * Attempts to publish an event to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialized) 
     * event from the ring buffer and publishing the claimed sequence
     * after translation.  Will return false if specified capacity
     * was not available.
     * 
     * @param translator The user specified translation for the event
     * @param capacity The capacity that should be available before publishing
     * @return true if the value was published, false if there was insufficient
     * capacity.
     */
    public boolean tryPublishEvent(EventTranslator<E> translator, int capacity)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }
    
    /**
     * Allows one user supplied argument.
     * 
     * @see #publishEvent(EventTranslator)
     * @param translator The user specified translation for the event
     * @param arg0 A user supplied argument.
     */
    public <A> void publishEvent(final EventTranslatorOneArg<E, A> translator, final A arg0)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence, arg0);
    }
    
    /**
     * Allows one user supplied argument.
     * 
     * @see #tryPublishEvent(EventTranslator, int)
     * @param translator The user specified translation for the event
     * @param capacity The capacity that should be available before publishing
     * @param arg0 A user supplied argument.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     */
    public <A> boolean tryPublishEvent(EventTranslatorOneArg<E, A> translator, int capacity, A arg0)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence, arg0);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }
    
    /**
     * Allows two user supplied arguments.
     * 
     * @see #publishEvent(EventTranslator)
     * @param translator The user specified translation for the event
     * @param arg0 A user supplied argument.
     * @param arg1 A user supplied argument.
     */
    public <A, B> void publishEvent(final EventTranslatorTwoArg<E, A, B> translator, final A arg0, final B arg1)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence, arg0, arg1);
    }
        
    /**
     * Allows two user supplied arguments.
     * 
     * @see #tryPublishEvent(EventTranslator, int)
     * @param translator The user specified translation for the event
     * @param capacity The capacity that should be available before publishing
     * @param arg0 A user supplied argument.
     * @param arg1 A user supplied argument.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     */
    public <A, B> boolean tryPublishEvent(EventTranslatorTwoArg<E, A, B> translator, int capacity, final A arg0, final B arg1)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence, arg0, arg1);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }
    
    /**
     * Allows three user supplied arguments
     *
     * @see #publishEvent(EventTranslator)
     * @param translator The user specified translation for the event
     * @param arg0 A user supplied argument.
     * @param arg1 A user supplied argument.
     * @param arg2 A user supplied argument.
     */
    public <A, B, C> void publishEvent(final EventTranslatorThreeArg<E, A, B, C> translator,
                                       final A arg0, final B arg1, final C arg2)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence, arg0, arg1, arg2);
    }
    
    /**
     * Allows three user supplied arguments
     *
     * @see #publishEvent(EventTranslator)
     * @param translator The user specified translation for the event
     * @param capacity The capacity that should be available before publishing
     * @param arg0 A user supplied argument.
     * @param arg1 A user supplied argument.
     * @param arg2 A user supplied argument.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     */
    public <A, B, C> boolean tryPublishEvent(EventTranslatorThreeArg<E, A, B, C> translator, int capacity,
                                             final A arg0, final B arg1, final C arg2)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence, arg0, arg1, arg2);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * Allows a variable number of user supplied arguments
     *
     * @see #publishEvent(EventTranslator)
     * @param translator The user specified translation for the event
     * @param args User supplied arguments.
     */
    public void publishEvent(final EventTranslatorVararg<E> translator, final Object...args)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence, args);
    }
    
    /**
     * Allows a variable number of user supplied arguments
     *
     * @see #publishEvent(EventTranslator)
     * @param translator The user specified translation for the event
     * @param capacity The capacity that should be available before publishing
     * @param args User supplied arguments.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     */
    public boolean tryPublishEvent(EventTranslatorVararg<E> translator, int capacity, final Object...args)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence, args);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * Get the object that is preallocated within the ring buffer.  This differs from the {@link #get(long)} in that
     * is does not wait until the publisher indicates that object is available.  This method should only be used
     * by the publishing thread to get a handle on the preallocated event in order to fill it with data.
     * 
     * @param sequence for the event
     * @return event for the sequence
     */
    @SuppressWarnings("unchecked")
    public E getPreallocated(final long sequence)
    {
        return (E)entries[(int)sequence & indexMask];
    }

    private void translateAndPublish(final EventTranslator<E> translator, final long sequence)
    {
        try
        {
            translator.translateTo(getPreallocated(sequence), sequence);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private <A> void translateAndPublish(final EventTranslatorOneArg<E, A> translator,
                                         final long sequence, 
                                         final A arg0)
    {
        try
        {
            translator.translateTo(getPreallocated(sequence), sequence, arg0);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private <A, B> void translateAndPublish(final EventTranslatorTwoArg<E, A, B> translator,
                                            final long sequence, 
                                            final A arg0,
                                            final B arg1)
    {
        try
        {
            translator.translateTo(getPreallocated(sequence), sequence, arg0, arg1);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private <A, B, C> void translateAndPublish(final EventTranslatorThreeArg<E, A, B, C> translator,
                                               final long sequence, 
                                               final A arg0,
                                               final B arg1,
                                               final C arg2)
    {
        try
        {
            translator.translateTo(getPreallocated(sequence), sequence, arg0, arg1, arg2);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private <A> void translateAndPublish(final EventTranslatorVararg<E> translator,
                                         final long sequence, 
                                         final Object...args)
    {
        try
        {
            translator.translateTo(getPreallocated(sequence), sequence, args);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private void fill(final EventFactory<E> eventFactory)
    {
        for (int i = 0; i < entries.length; i++)
        {
            entries[i] = eventFactory.newInstance();
        }
    }
}
