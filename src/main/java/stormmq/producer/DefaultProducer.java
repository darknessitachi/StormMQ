package stormmq.producer; /**
 * Created by yang on 16-11-22.
 */

import stormmq.model.Message;
import stormmq.model.RequestResponseFromType;
import stormmq.model.RequestType;
import stormmq.model.StormRequest;
import stormmq.model.StormResponse;
import stormmq.producer.netty.ProducerConnectListener;
import stormmq.producer.netty.ProducerHandler;
import stormmq.producer.netty.ProducerConnection;
import stormmq.producer.netty.ProducerNettyConnect;

import java.util.ArrayList;
import java.util.List;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生产者类的实现类
 */
public class DefaultProducer implements Producer {
	
	private static AtomicLong callTimes = new AtomicLong(0L);
	
	private String brokerIP; // broker服务器ip地址.
	private ProducerConnection brokerConn; // 连接broker服务器的连接
	
	private List<ProducerConnection> broker_list; // 拓展的连接,解决网络IO瓶颈.
	private AtomicInteger requestId = new AtomicInteger(0); // 消息的ID
	private String groupId; // 组id.
	private String topic; // 生产者的topic.
	private boolean isRunning = false;
	private boolean isConnected = false;

	public DefaultProducer() {
		brokerIP = "127.0.0.1";
		brokerConn = new ProducerNettyConnect(brokerIP, 8888);
		broker_list = new ArrayList<>();
		int num = Runtime.getRuntime().availableProcessors() / 2;
		for (int i = 0; i < num; ++i) {
			broker_list.add(new ProducerNettyConnect(brokerIP, 8888));
		}
	}

    @Override
    public void start() {
        //设置处理器
		brokerConn.setHandler(new ProducerHandler(brokerConn, new ProducerConnectListener() {
			@Override
			public void onDisconnected(String t) {
				// System.out.println("smq.DefaultProducer::::test");
				synchronized (this) {
					if (isRunning) {
						isConnected = false;
						restartConnect();
					}
				}
			}
		}));
        //连接服务器
        brokerConn.connect();
        
		for (ProducerConnection conn : broker_list) {
			// System.out.println("connect");
			conn.setHandler(new ProducerHandler(conn));
			conn.connect();
		}
        isRunning = true;
        isConnected = true;
    }

    @Override
    public void setTopic(String paramString) {
        this.topic = paramString;
    }

    @Override
    public void setGroupId(String paramString) {
        this.groupId = paramString;
    }
    
    /**
     * 选择一个连接.
     * @return
     */
    synchronized ProducerConnection select(){
        /**
         * getAndIncrment()返回的原子自增前1前的值.
         * incrementAndGet返回的自增1后的新值.
         */
		// System.out.println("callTimes: "+callTimes +
		// "callTimes.getAndIncrement:"+callTimes.getAndIncrement());
		int d = (int) (callTimes.getAndIncrement() % (broker_list.size() + 1));
		System.out.println("smq.DefaultProducer--->:::d" + d);
		if (d == 0) {
			return brokerConn;
		} else {
			return broker_list.get(d - 1);
		}
    }
    
    public void restartConnect(){
		// System.out.println("smq.DefaultProducer::restartConnect");
		brokerIP = "127.0.0.1";
		brokerConn = new ProducerNettyConnect(brokerIP, 8888);
		broker_list = new ArrayList<ProducerConnection>();
		int num = Runtime.getRuntime().availableProcessors();
		for (int i = 0; i < num; ++i) {
			broker_list.add(new ProducerNettyConnect(brokerIP, 8888));
		}
        //设置处理器
        brokerConn.setHandler(new ProducerHandler(brokerConn, new ProducerConnectListener() {
            @Override
            public void onDisconnected(String t) {
                synchronized (this){
                    if(isRunning){
                        isConnected = false;
                        restartConnect();
                    }
                }
            }
        }));
        try{
            brokerConn.connect();
            for(ProducerConnection conn : broker_list){
                conn.setHandler(new ProducerHandler(conn));
                conn.connect();
            }
        }catch (Exception e){
            try {
                Thread.sleep(3000);
            }catch (InterruptedException le){
                le.printStackTrace();
            }
            restartConnect();
        }
        isConnected = true;
	}

	@Override
	public SendResult sendMessage(Message paramMessage) {
		if (!isRunning) {
			return null;
		}
		if (!isConnected) { // 未连接,broker可能是重启了
			return SendResult.createFailResult();
		}
        //要发送的消息设置主题
        paramMessage.setTopic(topic);
        paramMessage.setBornTime(System.currentTimeMillis());
        //构建请求信息ｎ
        StormRequest request = new StormRequest();
        //System.out.println("requested.length0:"+ request.toString().length());
		request.setRequestId(requestId.incrementAndGet() + "");
		request.setParameters(paramMessage); // 设置要发送的message;
		request.setRequestType(RequestType.Message);
		request.setFromType(RequestResponseFromType.Produce);
        paramMessage.setMsgId(request.getRequestId()); //请求id一般和消息id一致.

        //同步发送信息
		// System.out.println("requested.length:"+request.toString().length());
		StormResponse response = (StormResponse) select().send(request);// 按照一定的顺序选择一个连接发送数据
		// StormResponse response = (StormResponse)brokerConn.Send(request);
        SendResult result = (SendResult)response.getResponse();
        return  result;
    }

    @Override
    public void asyncSendMessage(Message paramMessage, SendCallback paramSendCallback) {
		// 设置要发送的消息的主题
        paramMessage.setTopic(topic);
        paramMessage.setBornTime(System.currentTimeMillis());

        //构建请求信息
        StormRequest request = new StormRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setParameters(paramMessage); //设置要发送的内容
        //发送的请求为消息
        request.setRequestType(RequestType.Message);
        request.setFromType(RequestResponseFromType.Produce);
        paramMessage.setMsgId(request.getRequestId());

        brokerConn.send(request,paramSendCallback);
    }

	@Override
	public void stop() {
		if (isRunning) {
			isRunning = false;
			brokerConn.close();
			for (ProducerConnection stormProducerConnection : broker_list) {
				stormProducerConnection.close();
			}
		}
	}
}
