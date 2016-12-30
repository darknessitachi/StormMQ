package stormmq.producer.netty;

/**
 * Created by yang on 16-11-22.
 */
import io.netty.channel.ChannelInboundHandlerAdapter;
import stormmq.model.InvokeFuture;
import stormmq.model.StormRequest;
import stormmq.smq.SendCallback;

/**
 * producer和broker之间的连接.
 */
public interface StormProducerConnection {
    void init();
    void connect();
    void connect(String host,int port);
    void setHandler(ChannelInboundHandlerAdapter handler);
    Object Send(StormRequest request);
    void Send(StormRequest request, final SendCallback listener);
    void close();
    boolean isConnected();
    boolean isClosed();
    public boolean ContrainsFuture(String key);
    public InvokeFuture<Object> removeFuture(String key);
    public void setTimeOut(long timeOut);

}
