package stormmq.StormTest;


import stormmq.model.Message;
import stormmq.producer.DefaultProducer;
import stormmq.producer.Producer;
import stormmq.producer.SendResult;
import stormmq.producer.SendStatus;

/**
 * Created by yang on 16-11-25.
 */
public class ProducerTest {
    public static void main(String[] args) {
        Producer producer = new DefaultProducer();

        producer.setGroupId("PG-test");
        producer.setTopic("T-test");
        producer.start();
        Message message = new Message();
        message.setBody("Hello MOM".getBytes());
        message.setProperty("area","us");

        SendResult result = producer.sendMessage(message);
        if(result.getStatus().equals(SendStatus.SUCCESS)){
            System.out.println("Send success:"+result.getMsgId());
        }
    }

}
