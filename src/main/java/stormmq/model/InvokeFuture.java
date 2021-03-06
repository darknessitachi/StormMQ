package stormmq.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by yang on 16-11-22.
 */
public class InvokeFuture<T> {

	private Semaphore semaphore = new Semaphore(0);
	private T result;
	private List<InvokeListener<T>> listeners = new ArrayList<>();
	private String requestId;
	private Throwable cause;

	public InvokeFuture() {
	}
	
	public void setCause(Throwable cause) {
		this.cause = cause;
		notifyListeners();
		semaphore.release(Integer.MAX_VALUE - semaphore.availablePermits());
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public void setResult(T result) {
		this.result = result;
		notifyListeners();
		semaphore.release(Integer.MAX_VALUE - semaphore.availablePermits());
	}

	public T getResult(long timeout, TimeUnit unit) {
		try {
			if (!semaphore.tryAcquire(timeout, unit)) {
				throw new RuntimeException("time out");
			}
		} catch (InterruptedException e) {
			throw new RuntimeException();
		}
		if (cause != null)
			return null;
		return result;
	}

	public void addInvokerListener(InvokeListener<T> listener) {
		this.listeners.add(listener);
	}

	private void notifyListeners() {
		for (InvokeListener<T> listener : listeners) {
			listener.onResponse(result);
		}
	}
}
