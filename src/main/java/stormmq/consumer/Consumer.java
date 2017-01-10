package stormmq.consumer;

/**
 * Created by yang on 16-11-24.
 */
public interface Consumer {
	void start();

	void subscribe(String paramString1, String paramString2, MessageListener listener);

	void setGroupId(String paramString);

	void stop();
}
