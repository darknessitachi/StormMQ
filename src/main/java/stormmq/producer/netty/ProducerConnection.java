package stormmq.producer.netty;

import stormmq.model.InvokeFuture;
import stormmq.model.StormRequest;
import stormmq.model.StormResponse;
import stormmq.producer.SendCallback;

/**
 * producer和broker之间的连接.
 */
public interface ProducerConnection {
	
	void init();

	void connect();

	void connect(String host, int port);

	void setHandler(ProducerHandler handler);

	StormResponse send(StormRequest request);

	void send(StormRequest request, final SendCallback listener);

	void close();

	boolean isConnected();

	boolean isClosed();

	public boolean contrainsFuture(String key);

	public InvokeFuture<StormResponse> removeFuture(String key);

	public void setTimeOut(long timeOut);

}
