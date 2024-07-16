package com.lmax.disruptor;

/**
 * Write interface for {@link RingBuffer}.
 *
 * <p>RingBuffer 的写接口
 *
 * @param <E> The event type
 */
public interface EventSink<E>
{
    /**
     * Publishes an event to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialised)
     * event from the ring buffer and publishing the claimed sequence
     * after translation.
     *
     * <p>发布事件到 RingBuffer 中。
     * 它处理获取下一个序列，从 RingBuffer 中获取当前（未初始化）事件，
     * 并在翻译后发布声明的序列。</p>
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
     * <p>尝试发布事件到 RingBuffer 中。
     * 它处理获取下一个序列，从 RingBuffer 中获取当前（未初始化）事件，
     * 并在翻译后发布声明的序列。
     * 如果指定的容量不可用，则返回 false。</p>
     *
     * @param translator The user specified translation for the event
     * @return true if the value was published, false if there was insufficient
     * capacity.
     */
    boolean tryPublishEvent(EventTranslator<E> translator);

    /**
     * Allows one user supplied argument.
     *
     * <p>允许一个用户提供的参数。</p>
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
     * <p>允许一个用户提供的参数。</p>
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
     * <p>允许两个用户提供的参数。</p>
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
     * <p>允许两个用户提供的参数。</p>
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
     * <p>允许三个用户提供的参数</p>
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
     * <p>允许三个用户提供的参数</p>
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
     * <p>允许可变数量的用户提供的参数</p>
     *
     * @param translator The user specified translation for the event
     * @param args       User supplied arguments.
     * @see #publishEvent(EventTranslator)
     */
    void publishEvent(EventTranslatorVararg<E> translator, Object... args);

    /**
     * Allows a variable number of user supplied arguments
     *
     * <p>允许可变数量的用户提供的参数</p>
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
     * <p>发布多个事件到 RingBuffer 中。
     * 它处理获取下一个序列，从 RingBuffer 中获取当前（未初始化）事件，
     * 并在翻译后发布声明的序列。</p>
     *
     * <p>With this call the data that is to be inserted into the ring
     * buffer will be a field (either explicitly or captured anonymously),
     * therefore this call will require an instance of the translator
     * for each value that is to be inserted into the ring buffer.</p>
     *
     * <p>通过此调用，要插入到 RingBuffer 中的数据将是一个字段（显式或匿名捕获），
     * 因此此调用将需要为要插入到 RingBuffer 中的每个值的翻译器实例。</p>
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
     * <p>发布多个事件到 RingBuffer 中。
     * 它处理获取下一个序列，从 RingBuffer 中获取当前（未初始化）事件，
     * 并在翻译后发布声明的序列。</p>
     *
     * <p>With this call the data that is to be inserted into the ring
     * buffer will be a field (either explicitly or captured anonymously),
     * therefore this call will require an instance of the translator
     * for each value that is to be inserted into the ring buffer.</p>
     *
     * <p>通过此调用，要插入到 RingBuffer 中的数据将是一个字段（显式或匿名捕获），
     * 因此此调用将需要为要插入到 RingBuffer 中的每个值的翻译器实例。</p>
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
     * <p>尝试发布多个事件到 RingBuffer 中。
     * 它处理获取下一个序列，从 RingBuffer 中获取当前（未初始化）事件，
     * 并在翻译后发布声明的序列。
     * 如果指定的容量不可用，则返回 false。</p>
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
     * <p>尝试发布多个事件到 RingBuffer 中。
     * 它处理获取下一个序列，从 RingBuffer 中获取当前（未初始化）事件，
     * 并在翻译后发布声明的序列。
     * 如果指定的容量不可用，则返回 false。</p>
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
     * <p>允许每个事件一个用户提供的参数。</p>
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
     * <p>允许每个事件一个用户提供的参数。</p>
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
     * <p>允许一个用户提供的参数。</p>
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
     * <p>允许一个用户提供的参数。</p>
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
     * <p>允许每个事件两个用户提供的参数。</p>
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
     * <p>允许每个事件两个用户提供的参数。</p>
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
     * <p>允许每个事件两个用户提供的参数。</p>
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
     * <p>允许每个事件两个用户提供的参数。</p>
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
     * <p>允许每个事件三个用户提供的参数。</p>
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
     * <p>允许每个事件三个用户提供的参数。</p>
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
     * <p>允许每个事件三个用户提供的参数。</p>
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
     * <p>允许每个事件可变数量的用户提供的参数。</p>
     *
     * @param translator The user specified translation for the event
     * @param args       User supplied arguments, one Object[] per event.
     * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
     */
    void publishEvents(EventTranslatorVararg<E> translator, Object[]... args);

    /**
     * Allows a variable number of user supplied arguments per event.
     *
     * <p>允许每个事件可变数量的用户提供的参数。</p>
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
     * <p>允许每个事件可变数量的用户提供的参数。</p>
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
     * <p>允许每个事件可变数量的用户提供的参数。</p>
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