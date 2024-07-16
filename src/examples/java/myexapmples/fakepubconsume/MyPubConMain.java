package myexapmples.fakepubconsume;

public class MyPubConMain
{
    public static void main(String[] args) throws InterruptedException
    {
        final MyPubCon<Long> longMyPubCon = new MyPubCon<>();
        longMyPubCon.addConsumer(event -> System.out.println("Event1: " + event));
        longMyPubCon.addConsumer(event -> System.out.println("Event2: " + event));
        longMyPubCon.start();

        for (int i = 0; true; i++)
        {
            longMyPubCon.publish((long) i);
            Thread.sleep(1000L);
        }
    }
}
