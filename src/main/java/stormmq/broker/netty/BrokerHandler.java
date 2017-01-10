package stormmq.broker.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import stormmq.broker.ClientChannelInfo;
import stormmq.broker.ConsumerManager;
import stormmq.broker.SendHelper;
import stormmq.consumer.ConsumeResult;
import stormmq.model.InvokeFuture;
import stormmq.model.Message;
import stormmq.model.RequestResponseFromType;
import stormmq.model.ResponseType;
import stormmq.model.StormRequest;
import stormmq.model.StormResponse;
import stormmq.model.SubscriptRequestinfo;


/**
 * Created by yang on 16-11-24.
 */
@ChannelHandler.Sharable
//当此handle可以被多次使用,即在多线程中,是多个连接的处理器.
public class BrokerHandler extends SimpleChannelInboundHandler<StormRequest> {
	
    //对Producer发送的消息进行处理.只有一种消息就是Message
    private MessageListener  producerListener;
    //对consumer发送的消息进行处理,有两种消息,一种是订阅信息,一种是消费信息
    private MessageListener consumerRequestListener;

    public void setProducerListener(MessageListener producerListener) {
        this.producerListener = producerListener;
    }

    public void setConsumerRequestListener(MessageListener consumerRequestListener) {
        this.consumerRequestListener = consumerRequestListener;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        System.out.println("connect from :"+ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        System.out.println("channelInactive");
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
     //   System.out.println("ReadComplete");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        System.out.println("server exceptionCaught");
    }

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, StormRequest request) throws Exception {
		// System.out.println("received request");

        //构建响应消息,不论是消费者还是生产者,发给的broker的消息都是用StormRequest包装,回应的消息都是用StormResponse响应.
        StormResponse response = new StormResponse();
        response.setRequestId(request.getRequestId());
        response.setFromtype(RequestResponseFromType.Broker);
      //  System.out.println("request.getRequestType():"+request.getRequestType());
        switch(request.getRequestType()){
	        case Message:
				// System.out.println("received Message");
				Message message = (Message) request.getParameters();
	                //当有多个生产者时同时刷如磁盘的数据量根据生产者上升
				if (request.getFromType() == RequestResponseFromType.Produce) {
					// System.out.println("Conf");
					Conf.Increase(message.getTopic());
				}
	            producerListener.onProducerMessageReceived(message,request.getRequestId(),ctx.channel());
	            //返回给producer消息发送的状态,是否成功.
	            break;
            case ConsumeResult:
               // System.out.println("receiced ConsumeResult");
                ConsumeResult result = (ConsumeResult)request.getParameters();

                String key = response.getRequestId();
                if(SendHelper.containsFuture(key)){
                    InvokeFuture<Object> future = SendHelper.removeFuture(key);
                    if(future == null){
                        return;
                    }else {
                        future.setResult(request);
                    }
                    consumerRequestListener.onConsumerResultReceived(result);
                    consumerRequestListener.onRequest(request);
                }
                break;
            case Subscript:
                // System.out.println("received Subscript");
                //收到的是来自consumer的订阅消息

                SubscriptRequestinfo subscriptRequestinfo = (SubscriptRequestinfo)request.getParameters();
                // System.out.println("groupId:"+subscriptRequestinfo.getGroupId());
                String clientKey = subscriptRequestinfo.getClientKey();
                ClientChannelInfo channelInfo = new ClientChannelInfo(ctx.channel(),clientKey);
                consumerRequestListener.onConsumerSubcriptReceived(subscriptRequestinfo,channelInfo);
                consumerRequestListener.onRequest(request);

                response.setResponseType(ResponseType.AckSubscript);
                //System.out.println("发给消费者ack前");
                ctx.writeAndFlush(response);
                //System.out.println("发给消费者ack后");
                break;
            case Unsubscript:
                String clientId = (String) request.getParameters();
                ConsumerManager.stopConsumer(clientId);
                break;
            default:
                System.out.println("type invalid");
                break;
        }
	}
}
