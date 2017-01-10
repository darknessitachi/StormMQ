package stormmq.consumer.netty;

import io.netty.channel.ChannelInboundHandlerAdapter;
import stormmq.model.InvokeFuture;
import stormmq.model.StormRequest;
import stormmq.model.StormResponse;

/**
 * Created by yang on 16-11-24.
 */
public interface ConsumerConnection {
	void init();

	void connect();

	void connect(String host, int port);

	void setHandle(ConsumerHandler hanler);

	StormResponse send(StormRequest request);

	void SendSync(StormRequest request);

	void close();

	boolean isConnected();

	boolean isClosed();

	public boolean ContainsFuture(String key);

	public InvokeFuture<StormResponse> removeFuture(String key);

	public void setTimeOut(long timeOut);
}
