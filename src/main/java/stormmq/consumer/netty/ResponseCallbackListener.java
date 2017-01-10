package stormmq.consumer.netty;

import stormmq.consumer.ConsumeResult;
import stormmq.model.StormResponse;

/**
 * Created by yang on 16-11-24.
 */
public interface ResponseCallbackListener {
	ConsumeResult onResponse(StormResponse response);

	void onTimeout();

	void onException(Throwable e);

	void onDisconnect(String msg);
}
