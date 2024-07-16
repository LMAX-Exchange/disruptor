package myexapmples.fakepubconsume;

public interface Consumer<T>
{
    void consume(T event);
}
