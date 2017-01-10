package stormmq.consumer.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import stormmq.consumer.ConsumeResult;
import stormmq.model.InvokeFuture;
import stormmq.model.RequestResponseFromType;
import stormmq.model.RequestType;
import stormmq.model.StormRequest;
import stormmq.model.StormResponse;

/**
 * Created by yang on 16-11-24.
 */
public class ConsumerHandler extends SimpleChannelInboundHandler<StormResponse> {
	
	private ConsumerConnection connect;
	private Throwable cause;
	private ResponseCallbackListener listener;

	public ConsumerHandler(ConsumerConnection conn, ResponseCallbackListener listener) {
		this.connect = conn;
		this.listener = listener;
	}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        //super.exceptionCaught(ctx, cause);
        this.cause=cause;
        System.out.println("StormHandler caught exception");
        if(listener!=null)
            listener.onException(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //super.channelInactive(ctx);
        if(listener != null){
            listener.onDisconnect(ctx.channel().remoteAddress().toString());
        }
    }

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, StormResponse response) throws Exception {
	//  System.out.println("收到消息");

        String key = response.getRequestId();
        if(connect.ContainsFuture(key)){
            InvokeFuture<StormResponse> future = connect.removeFuture(key);
            //没有找到对应的发送请求,则返回
            if(future == null){
                return;
            }
            if(this.cause != null){
                //设置异常结果,会触发里面的回调函数
               // System.out.println("StormConr  sumerHandler:::-->cause:"+cause);
                future.setCause(cause);
                if(listener != null)
                    listener.onException(cause);
			} else {
				future.setResult(response);
			}
		} else {
            //如果不是consumer主动发送的数据,则说明是服务器主动发送的消息,则调用消息收到
            if(listener != null){
                ConsumeResult result = (ConsumeResult)listener.onResponse(response);
                //回答consumer的消费情况,相当于服务器调用rpc
                StormRequest request = new StormRequest();
                request.setRequestId(response.getRequestId());
                request.setFromType(RequestResponseFromType.Consumer);
                request.setRequestType(RequestType.ConsumeResult);
                request.setParameters(result);

                ctx.writeAndFlush(request);
            }
        }
	}
}
