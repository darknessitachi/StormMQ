package stormmq.broker.netty;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import stormmq.broker.SemaphoreManager;
import stormmq.broker.TaskManager;
import stormmq.model.StormRequest;
import stormmq.model.StormResponse;
import stormmq.serializer.RpcDecoder;
import stormmq.serializer.RpcEncoder;

/**
 * Created by yang on 16-11-24.
 */
public class DefaultBrokerServer implements BrokerServer {
	
	private ServerBootstrap bootstrap;

	public DefaultBrokerServer() {
		init();
	}

	@Override
	public void init() {
		final BrokerHandler handler = new BrokerHandler();

		// 设置两个监听器
		handler.setProducerListener(new ProducerMessageListener());
		handler.setConsumerRequestListener(new ConsumerMessageListener());

		EventLoopGroup bossGroup = new NioEventLoopGroup();
		// 处理事件的线程池
		EventLoopGroup workerGroup = new NioEventLoopGroup(30);
		try {
			bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.handler(new LoggingHandler(LogLevel.INFO))
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel socketChannel) throws Exception {
							socketChannel.pipeline().addLast(new RpcDecoder(StormRequest.class));
							socketChannel.pipeline().addLast(new RpcEncoder(StormResponse.class));
							socketChannel.pipeline().addLast(handler);
						}
					}).option(ChannelOption.SO_KEEPALIVE, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void start() {
		try {
			ChannelFuture cFuture = bootstrap.bind(8888).sync();
			// 创建一个写文件的锁
			SemaphoreManager.createSemaphore("SendTask");
			// 创建一个发送ack消息的信号量
			SemaphoreManager.createSemaphore("Ack");
			// 恢复之前的发送任务到队列
			TaskManager.RecoverySendTask();

			// 获得系统可并行的线程数
			int availableProcessors = Runtime.getRuntime().availableProcessors();
			ExecutorService executorService = Executors.newFixedThreadPool(availableProcessors * 2 + 2);
			// ExecutorService executorService = Executors.newFixedThreadPool(1);
			// 启动发送线程
			for (int i = 0; i < availableProcessors; ++i) {
				executorService.execute(new SendThread());
				System.out.println("start sendThread" + (i + 1));
			}
			// 启动ack发送线程
			for (int i = 0; i < availableProcessors; ++i) {
				executorService.execute(new AckSendThread());
				System.out.println("start ack sendThread:" + (i + 1));
			}
			// 启动一个记录发送tps的线程
			executorService.execute(new RecordThread());
			// 启动刷盘线程
			executorService.execute(new FlushThread());
			
			cFuture.channel().closeFuture().sync();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Broker start error!");
		}
	}

	public static void main(String[] args) {
		BrokerServer brokerServer = new DefaultBrokerServer();
		brokerServer.start();
	}
}
