package stormmq.producer.netty;

/**
 * Created by yang on 16-11-22.
 */
public interface ProducerConnectListener {
	void onDisconnected(String t);
}
