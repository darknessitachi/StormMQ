package stormmq.producer;

import stormmq.model.Message;

/**
 * Created by yang on 16-11-22.
 */
public interface Producer {
	
	void start();

	void setTopic(String paramString);

	void setGroupId(String paramString);

	SendResult sendMessage(Message paramMessage);

	void asyncSendMessage(Message paramMessage, SendCallback paramSendCallback);

	void stop();
}
