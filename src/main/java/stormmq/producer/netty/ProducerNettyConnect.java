package stormmq.producer.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import stormmq.model.InvokeFuture;
import stormmq.model.InvokeListener;
import stormmq.model.StormRequest;
import stormmq.model.StormResponse;
import stormmq.producer.SendCallback;
import stormmq.producer.SendResult;
import stormmq.serializer.RpcDecoder;
import stormmq.serializer.RpcEncoder;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by yang on 16-11-22.
 */
public class ProducerNettyConnect implements ProducerConnection {
	
    private InetSocketAddress inetAddr;
    private volatile Channel channel;
    //连接处理类
    private ProducerHandler handle;
    private Map<String,InvokeFuture<StormResponse>> futures = new ConcurrentHashMap<>();
	private Map<String, Channel> channels = new ConcurrentHashMap<>(); // ip和channel的映射关系
    private Bootstrap bootstrap;
    private long timeout = 10_000; //默认超时时间.
    private boolean connected = false;

	public ProducerNettyConnect(String host, int port) {
		inetAddr = new InetSocketAddress(host, port);
	}

	// 设置要处理连接的类
	public void setHandle(ProducerHandler handle) {
		this.handle = handle;
	}

	public Channel getChannel(String key) {
		return channels.get(key);
	}

	@Override
	public void init() {
        try {
            EventLoopGroup group = new NioEventLoopGroup();
            bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                        	socketChannel.pipeline().addLast(new RpcEncoder(StormRequest.class));//编码
                            socketChannel.pipeline().addLast(new RpcDecoder(StormResponse.class));//解码
                            socketChannel.pipeline().addLast(handle);
                        }
                    }).option(ChannelOption.SO_KEEPALIVE,true);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     *同步连接broker.
     */
    @Override
    public void connect() {
        //连接的时候进行初始化
		if (handle != null) {
			init();
		} else {
			throw new RuntimeException("handle is null");
		}
        try{
            ChannelFuture future = bootstrap.connect(this.inetAddr).sync();
			channels.put(this.inetAddr.toString(), future.channel());
            connected = true;
        }catch (InterruptedException e){
            System.out.println("StormProducerNettyConnect exception:" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 异步连接broker
     * @param host
     * @param port
     */
    @Override
    public void connect(String host, int port) {
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host,port));
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                Channel channel = channelFuture.channel();
                //添加进连接数组.
                channels.put(channel.remoteAddress().toString(),channel);
            }
        });
    }

    @Override
    public void setHandler(ProducerHandler handler){
        this.handle = handler;
    }

    /**
     * 同步发送消息给服务器,并且收到服务器结果
     * @param request
     * @return
     */
    @Override
    public StormResponse send(StormRequest request) {
		if (channel == null) {
			channel = getChannel(inetAddr.toString());
		}
		if (channel != null) {
            final InvokeFuture<StormResponse> future = new InvokeFuture<>();
			futures.put(request.getRequestId(), future);
			// 设置本次请求的id.
			future.setRequestId(request.getRequestId());
			// System.out.println("StrormProducerNettyConnect:writeAndFlush
			// Before:");
			ChannelFuture cFuture = channel.writeAndFlush(request);
			cFuture.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture channelFuture) throws Exception {
					// System.out.println("StormProduceNettyConnect:"+"request发送完成");
					if (!channelFuture.isSuccess()) {
						future.setCause(channelFuture.cause());
					}
				}
			});
			
			try {
				Thread.sleep(0);
				StormResponse result = future.getResult(timeout, TimeUnit.MILLISECONDS);
				return result;
			} catch (RuntimeException e) {
				throw e;
			} catch (InterruptedException e) {
				e.printStackTrace();
				return null;
			} finally {
                //这个结果已经收到
                futures.remove(request.getRequestId());
            }
		}
		return null;
    }

    /**
     * 异步发送请求
     * @param request
     * @param listener
     */
    @Override
	public void send(StormRequest request, final SendCallback listener) {
		if (channel == null) {
			channel = getChannel(inetAddr.toString());
		}
        if(channel != null){
			final InvokeFuture<StormResponse> future = new InvokeFuture<>();
			futures.put(request.getRequestId(), future);
			// 设置这次请求的ID，
			future.setRequestId(request.getRequestId());
			// 设置回调函数
			future.addInvokerListener(new InvokeListener<StormResponse>() {
				@Override
				public void onResponse(StormResponse o) {
					StormResponse response = (StormResponse) o;
					// 回调函数
					listener.onResult((SendResult) response.getResponse());
				}
			});
			final ChannelFuture cfuture = channel.writeAndFlush(request);
			cfuture.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture channelFuture) throws Exception {
					if (!channelFuture.isSuccess()) {
						future.setCause(channelFuture.cause());
					}

				}
			});
			try {
				// Object result=future.getResult(timeout,
				// TimeUnit.MILLISECONDS);
			} catch (RuntimeException e) {
				throw e;
			} finally {
				// 移除已经收到的消息
				// futrues.remove(request.getRequestId());
			}
        }
    }

    @Override
    public void close() {
        if(channel != null){
            try{
                channel.close().sync();
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isClosed() {
        return (channel == null) || !channel.isOpen() || !channel.isWritable() || !channel.isActive();
    }

    @Override
    public boolean contrainsFuture(String key) {
        if(key == null){
            return false;
        }
        return futures.containsKey(key);
    }

    @Override
    public InvokeFuture<StormResponse> removeFuture(String key) {
		if (contrainsFuture(key)) {
			return futures.remove(key);
		}
		return null;
    }

    @Override
    public void setTimeOut(long timeOut) {
        this.timeout = timeOut;
    }
}
