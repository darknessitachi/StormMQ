package stormmq.producer.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import stormmq.model.InvokeFuture;
import stormmq.model.StormResponse;

/**
 * Created by yang on 16-11-22.
 */
public class ProducerHandler extends SimpleChannelInboundHandler<StormResponse> {
	
    private ProducerConnection connect;
    private Throwable cause;
    private ProducerConnectListener listener;

	public ProducerHandler(ProducerConnection conn) {
		this.connect = conn;
	}

	public ProducerHandler(ProducerConnection conn, ProducerConnectListener listener) {
		this.connect = conn;
		this.listener = listener;
	}

	@Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception{
        super.channelActive(ctx);
        System.out.println("connected on server: "+ ctx.channel().remoteAddress().toString());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        this.cause = cause;
        cause.printStackTrace();
        System.out.println("StormHandler caught exception");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		System.out.println("disconnect to broker");
		if (listener != null) {
			listener.onDisconnected(ctx.channel().remoteAddress().toString());
		}
    }

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, StormResponse response) throws Exception {
		// System.out.println("channelRead");
        String key = response.getRequestId();
		if (connect.contrainsFuture(key)) {
			InvokeFuture<StormResponse> future = connect.removeFuture(key);
			// 没有找到的发送请求
			if (future == null) {
				return;
			}
			if (this.cause != null) {
				// 设置异常结果,会触发里面的回调函数
				future.setCause(cause);
			} else {
				future.setResult(response);
			}
        }
	}
}
