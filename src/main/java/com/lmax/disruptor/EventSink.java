package com.lmax.disruptor;

public interface EventSink<E>
{
    /**
     * Publishes an event to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialised)
     * event from the ring buffer and publishing the claimed sequence
     * after translation.
     *
     * @param translator The user specified translation for the event
     */
    void publishEvent(EventTranslator<E> translator);

    /**
     * Attempts to publish an event to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialised)
     * event from the ring buffer and publishing the claimed sequence
     * after translation.  Will return false if specified capacity
     * was not available.
     *
     * @param translator The user specified translation for the event
     * @return true if the value was published, false if there was insufficient
     * capacity.
     */
    boolean tryPublishEvent(EventTranslator<E> translator);

    /**
     * Allows one user supplied argument.
     *
     * @param <A> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       A user supplied argument.
     * @see #publishEvent(EventTranslator)
     */
    <A> void publishEvent(EventTranslatorOneArg<E, A> translator, A arg0);

    /**
     * Allows one user supplied argument.
     *
     * @param <A> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       A user supplied argument.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #tryPublishEvent(EventTranslator)
     */
    <A> boolean tryPublishEvent(EventTranslatorOneArg<E, A> translator, A arg0);

    /**
     * Allows two user supplied arguments.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       A user supplied argument.
     * @param arg1       A user supplied argument.
     * @see #publishEvent(EventTranslator)
     */
    <A, B> void publishEvent(EventTranslatorTwoArg<E, A, B> translator, A arg0, B arg1);

    /**
     * Allows two user supplied arguments.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       A user supplied argument.
     * @param arg1       A user supplied argument.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #tryPublishEvent(EventTranslator)
     */
    <A, B> boolean tryPublishEvent(EventTranslatorTwoArg<E, A, B> translator, A arg0, B arg1);

    /**
     * Allows three user supplied arguments
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param <C> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       A user supplied argument.
     * @param arg1       A user supplied argument.
     * @param arg2       A user supplied argument.
     * @see #publishEvent(EventTranslator)
     */
    <A, B, C> void publishEvent(EventTranslatorThreeArg<E, A, B, C> translator, A arg0, B arg1, C arg2);

    /**
     * Allows three user supplied arguments
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param <C> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       A user supplied argument.
     * @param arg1       A user supplied argument.
     * @param arg2       A user supplied argument.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #publishEvent(EventTranslator)
     */
    <A, B, C> boolean tryPublishEvent(EventTranslatorThreeArg<E, A, B, C> translator, A arg0, B arg1, C arg2);

    /**
     * Allows a variable number of user supplied arguments
     *
     * @param translator The user specified translation for the event
     * @param args       User supplied arguments.
     * @see #publishEvent(EventTranslator)
     */
    void publishEvent(EventTranslatorVararg<E> translator, Object... args);

    /**
     * Allows a variable number of user supplied arguments
     *
     * @param translator The user specified translation for the event
     * @param args       User supplied arguments.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #publishEvent(EventTranslator)
     */
    boolean tryPublishEvent(EventTranslatorVararg<E> translator, Object... args);

    /**
     * <p>Publishes multiple events to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialised)
     * event from the ring buffer and publishing the claimed sequence
     * after translation.</p>
     *
     * <p>With this call the data that is to be inserted into the ring
     * buffer will be a field (either explicitly or captured anonymously),
     * therefore this call will require an instance of the translator
     * for each value that is to be inserted into the ring buffer.</p>
     *
     * @param translators The user specified translation for each event
     */
    void publishEvents(EventTranslator<E>[] translators);

    /**
     * <p>Publishes multiple events to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialised)
     * event from the ring buffer and publishing the claimed sequence
     * after translation.</p>
     *
     * <p>With this call the data that is to be inserted into the ring
     * buffer will be a field (either explicitly or captured anonymously),
     * therefore this call will require an instance of the translator
     * for each value that is to be inserted into the ring buffer.</p>
     *
     * @param translators   The user specified translation for each event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch
     */
    void publishEvents(EventTranslator<E>[] translators, int batchStartsAt, int batchSize);

    /**
     * Attempts to publish multiple events to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialised)
     * event from the ring buffer and publishing the claimed sequence
     * after translation.  Will return false if specified capacity
     * was not available.
     *
     * @param translators The user specified translation for the event
     * @return true if the value was published, false if there was insufficient
     * capacity.
     */
    boolean tryPublishEvents(EventTranslator<E>[] translators);

    /**
     * Attempts to publish multiple events to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialised)
     * event from the ring buffer and publishing the claimed sequence
     * after translation.  Will return false if specified capacity
     * was not available.
     *
     * @param translators   The user specified translation for the event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch
     * @return true if all the values were published, false if there was insufficient
     * capacity.
     */
    boolean tryPublishEvents(EventTranslator<E>[] translators, int batchStartsAt, int batchSize);

    /**
     * Allows one user supplied argument per event.
     *
     * @param <A> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       A user supplied argument.
     * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
     */
    <A> void publishEvents(EventTranslatorOneArg<E, A> translator, A[] arg0);

    /**
     * Allows one user supplied argument per event.
     *
     * @param <A> Class of the user supplied argument
     * @param translator    The user specified translation for each event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch
     * @param arg0          An array of user supplied arguments, one element per event.
     * @see #publishEvents(EventTranslator[])
     */
    <A> void publishEvents(EventTranslatorOneArg<E, A> translator, int batchStartsAt, int batchSize, A[] arg0);

    /**
     * Allows one user supplied argument.
     *
     * @param <A> Class of the user supplied argument
     * @param translator The user specified translation for each event
     * @param arg0       An array of user supplied arguments, one element per event.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #tryPublishEvents(com.lmax.disruptor.EventTranslator[])
     */
    <A> boolean tryPublishEvents(EventTranslatorOneArg<E, A> translator, A[] arg0);

    /**
     * Allows one user supplied argument.
     *
     * @param <A> Class of the user supplied argument
     * @param translator    The user specified translation for each event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch
     * @param arg0          An array of user supplied arguments, one element per event.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #tryPublishEvents(EventTranslator[])
     */
    <A> boolean tryPublishEvents(EventTranslatorOneArg<E, A> translator, int batchStartsAt, int batchSize, A[] arg0);

    /**
     * Allows two user supplied arguments per event.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       An array of user supplied arguments, one element per event.
     * @param arg1       An array of user supplied arguments, one element per event.
     * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
     */
    <A, B> void publishEvents(EventTranslatorTwoArg<E, A, B> translator, A[] arg0, B[] arg1);

    /**
     * Allows two user supplied arguments per event.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param translator    The user specified translation for the event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch.
     * @param arg0          An array of user supplied arguments, one element per event.
     * @param arg1          An array of user supplied arguments, one element per event.
     * @see #publishEvents(EventTranslator[])
     */
    <A, B> void publishEvents(
        EventTranslatorTwoArg<E, A, B> translator, int batchStartsAt, int batchSize, A[] arg0,
        B[] arg1);

    /**
     * Allows two user supplied arguments per event.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       An array of user supplied arguments, one element per event.
     * @param arg1       An array of user supplied arguments, one element per event.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #tryPublishEvents(com.lmax.disruptor.EventTranslator[])
     */
    <A, B> boolean tryPublishEvents(EventTranslatorTwoArg<E, A, B> translator, A[] arg0, B[] arg1);

    /**
     * Allows two user supplied arguments per event.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param translator    The user specified translation for the event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch.
     * @param arg0          An array of user supplied arguments, one element per event.
     * @param arg1          An array of user supplied arguments, one element per event.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #tryPublishEvents(EventTranslator[])
     */
    <A, B> boolean tryPublishEvents(
        EventTranslatorTwoArg<E, A, B> translator, int batchStartsAt, int batchSize,
        A[] arg0, B[] arg1);

    /**
     * Allows three user supplied arguments per event.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param <C> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       An array of user supplied arguments, one element per event.
     * @param arg1       An array of user supplied arguments, one element per event.
     * @param arg2       An array of user supplied arguments, one element per event.
     * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
     */
    <A, B, C> void publishEvents(EventTranslatorThreeArg<E, A, B, C> translator, A[] arg0, B[] arg1, C[] arg2);

    /**
     * Allows three user supplied arguments per event.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param <C> Class of the user supplied argument
     * @param translator    The user specified translation for the event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The number of elements in the batch.
     * @param arg0          An array of user supplied arguments, one element per event.
     * @param arg1          An array of user supplied arguments, one element per event.
     * @param arg2          An array of user supplied arguments, one element per event.
     * @see #publishEvents(EventTranslator[])
     */
    <A, B, C> void publishEvents(
        EventTranslatorThreeArg<E, A, B, C> translator, int batchStartsAt, int batchSize,
        A[] arg0, B[] arg1, C[] arg2);

    /**
     * Allows three user supplied arguments per event.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param <C> Class of the user supplied argument
     * @param translator The user specified translation for the event
     * @param arg0       An array of user supplied arguments, one element per event.
     * @param arg1       An array of user supplied arguments, one element per event.
     * @param arg2       An array of user supplied arguments, one element per event.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
     */
    <A, B, C> boolean tryPublishEvents(EventTranslatorThreeArg<E, A, B, C> translator, A[] arg0, B[] arg1, C[] arg2);

    /**
     * Allows three user supplied arguments per event.
     *
     * @param <A> Class of the user supplied argument
     * @param <B> Class of the user supplied argument
     * @param <C> Class of the user supplied argument
     * @param translator    The user specified translation for the event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch.
     * @param arg0          An array of user supplied arguments, one element per event.
     * @param arg1          An array of user supplied arguments, one element per event.
     * @param arg2          An array of user supplied arguments, one element per event.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #publishEvents(EventTranslator[])
     */
    <A, B, C> boolean tryPublishEvents(
        EventTranslatorThreeArg<E, A, B, C> translator, int batchStartsAt,
        int batchSize, A[] arg0, B[] arg1, C[] arg2);

    /**
     * Allows a variable number of user supplied arguments per event.
     *
     * @param translator The user specified translation for the event
     * @param args       User supplied arguments, one Object[] per event.
     * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
     */
    void publishEvents(EventTranslatorVararg<E> translator, Object[]... args);

    /**
     * Allows a variable number of user supplied arguments per event.
     *
     * @param translator    The user specified translation for the event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch
     * @param args          User supplied arguments, one Object[] per event.
     * @see #publishEvents(EventTranslator[])
     */
    void publishEvents(EventTranslatorVararg<E> translator, int batchStartsAt, int batchSize, Object[]... args);

    /**
     * Allows a variable number of user supplied arguments per event.
     *
     * @param translator The user specified translation for the event
     * @param args       User supplied arguments, one Object[] per event.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
     */
    boolean tryPublishEvents(EventTranslatorVararg<E> translator, Object[]... args);

    /**
     * Allows a variable number of user supplied arguments per event.
     *
     * @param translator    The user specified translation for the event
     * @param batchStartsAt The first element of the array which is within the batch.
     * @param batchSize     The actual size of the batch.
     * @param args          User supplied arguments, one Object[] per event.
     * @return true if the value was published, false if there was insufficient
     * capacity.
     * @see #publishEvents(EventTranslator[])
     */
    boolean tryPublishEvents(EventTranslatorVararg<E> translator, int batchStartsAt, int batchSize, Object[]... args);

}