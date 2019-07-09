package com.example.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Test;

/**
 * Netty实现客户端辅助server测试.
 */
public class NettyClientTest {

    @Test
    public void testEchoClient() throws Exception {
        new EchoClient().run();
    }

    /**
     * Handler implementation for the echo client.  It initiates the ping-pong
     * traffic between the echo client and server by sending the first message to
     * the server.
     */
    static class EchoClientHandler extends ChannelInboundHandlerAdapter {

        private final ByteBuf firstMessage;

        /**
         * Creates a client-side handler.
         */
        public EchoClientHandler() {
            firstMessage = Unpooled.buffer(EchoClient.SIZE);
            for (int i = 0; i < firstMessage.capacity(); i++) {
                firstMessage.writeByte((byte) i);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(firstMessage);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ctx.write(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Close the connection when an exception is raised.
            cause.printStackTrace();
            ctx.close();
        }
    }

    /**
     * Sends one message when a connection is open and echoes back any received
     * data to the server.  Simply put, the echo client initiates the ping-pong
     * traffic between the echo client and server by sending the first message to
     * the server.
     */
    static class EchoClient {

        static final boolean SSL = System.getProperty("ssl") != null;
        static final String HOST = System.getProperty("host", "127.0.0.1");
        static final int PORT = Integer.parseInt(System.getProperty("port", "6050"));
        static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));

        void run() throws Exception {
            // Configure SSL.git
            final SslContext sslCtx;
            if (SSL) {
                sslCtx = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            } else {
                sslCtx = null;
            }

            // Configure the client.
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ChannelPipeline p = ch.pipeline();
                                if (sslCtx != null) {
                                    p.addLast(sslCtx.newHandler(ch.alloc(), HOST, PORT));
                                }
                                //p.addLast(new LoggingHandler(LogLevel.INFO));
                                p.addLast(new EchoClientHandler());
                            }
                        });

                // Start the client.
                ChannelFuture f = b.connect(HOST, PORT).sync();

                // Wait until the connection is closed.
                f.channel().closeFuture().sync();
            } finally {
                // Shut down the event loop to terminate all threads.
                group.shutdownGracefully();
            }
        }
    }
}
