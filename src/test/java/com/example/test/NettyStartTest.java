package com.example.test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * netty 初始测试和源码入口.
 */
public class NettyStartTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyStartTest.class);

    /**
     * 官方hello  world,观察ChannelInboundHandlerAdaptor,ByteBuf源码.
     */
    @Test
    public void testDiscard() throws InterruptedException {
        new DiscardServer(6050).run();
    }

    /**
     * Handles a server-side channel.
     * 忽略消息,打印日志.
     */
    private class DiscardServerHandler extends ChannelInboundHandlerAdapter { // (1)

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) { // (2)
            LOGGER.info("消息:{}", msg);
            // Discard the received data silently.
            ((ByteBuf) msg).release(); // (3)
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // (4)
            // Close the connection when an exception is raised.
            cause.printStackTrace();
            ctx.close();
        }
    }

    /**
     * 使用上面的处理器忽略一切消息的server
     */
    private class DiscardServer {

        private int port;//端口号.

        DiscardServer(int port) {
            this.port = port;
        }

        /**
         * 启动服务器方法.
         */
        void run() throws InterruptedException {
            EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap(); // (2)
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class) // (3)
                        .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new DiscardServerHandler());
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 128)          // (5)
                        .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

                // Bind and start to accept incoming connections.
                ChannelFuture f = b.bind(port).sync(); // (7)

                // Wait until the server socket is closed.
                // In this example, this does not happen, but you can do that to gracefully
                // shut down your server.
                f.channel().closeFuture().sync();
            } finally {
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            }
        }

    }

    /**
     * 带返回消息的server.
     */
    @Test
    public void testSendBack() throws InterruptedException {
        FeedBackServer server = new FeedBackServer(6050);
        server.run();
    }

    /**
     * 打印获得的数据并回复.
     */
    private class FeedBackHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf message = (ByteBuf) msg;

            StringBuilder sb = new StringBuilder();
            while (message.isReadable()) {
                sb.append((char) message.readByte());
            }
            String req = sb.toString();
            LOGGER.info("收到请求:{}", req);
            //根据官方解释,write到网线后netty会release掉这个msg,所以不用再release.
            ctx.writeAndFlush(msg);


        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            LOGGER.error("错误:{}",cause);
            ctx.close();
        }
    }


    private class FeedBackServer {
        private int port;

        FeedBackServer(int port) {
            this.port = port;
        }

        void run() throws InterruptedException {
            //1.boss组和worker组.
            EventLoopGroup boss = new NioEventLoopGroup();
            EventLoopGroup worker = new NioEventLoopGroup();
            //2.server bootstrap
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(boss, worker)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {

                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new FeedBackHandler());
                            }
                        }).option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);
                //绑定端口.
                ChannelFuture future = bootstrap.bind(port).sync();
                future.channel().closeFuture().sync();

            } finally {
                //关闭.
                worker.shutdownGracefully();
                boss.shutdownGracefully();
            }

        }
    }

}
