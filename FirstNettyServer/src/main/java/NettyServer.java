import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

/**
 * Created by Zhong Mingyi on 2020/12/11.
 */
public class NettyServer {
    private int port;

    public NettyServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws InterruptedException {
        new NettyServer(6688).startServer();
    }

    public void startServer() throws InterruptedException {
        final NettyServerHandler nettyServerHandler = new NettyServerHandler();
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();//1、创建NioEventLoopGroup
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();//2、创建ServerBootstrap
            bootstrap.group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)  //3、指定所用的NIO传输Channel
                    .localAddress(new InetSocketAddress(port)) //4、使用指定的端口设置Socket地址
                    .childHandler(new ChannelInitializer<SocketChannel>() { //5、添加一个NettyServerHandler到子Channel的ChannelPipeline
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(nettyServerHandler);//6、nettyServerHandler被注解为@Shareable，说明获取的是同一个对象
                        }
                    });
            //7、异步绑定服务器，调用sync方法阻塞等待直到绑定完成
            ChannelFuture channelFuture = bootstrap.bind().sync();
            System.out.println("122");
            //8、获取Channel的closeFuture，并且阻塞当前线程直到它完成
            channelFuture.channel().closeFuture().sync();
            System.out.println("12266");
        }finally {
            //关闭
            eventLoopGroup.shutdownGracefully().sync();
        }

    }
}
