package stormmq.consumer;

import stormmq.model.Message;

/**
 * Created by yang on 16-11-24.
 */
public interface MessageListener {
	ConsumeResult onMessage(Message paramMessage);
}
