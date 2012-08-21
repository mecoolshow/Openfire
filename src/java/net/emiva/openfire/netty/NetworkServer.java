package net.emiva.openfire.netty;


//import net.emiva.stax.balancing.RoundRobinBalancer;
//import net.emiva.stax.netty.SecureConnectionSslContextFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
        import org.jboss.netty.handler.execution.ExecutionHandler;
        import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jboss.netty.util.Timer;
import org.jboss.netty.util.HashedWheelTimer;

        import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
//import net.emiva.stax.netty.IdleClientHandler;

public class NetworkServer
{

    private ChannelFactory factory;
    private final ExecutionHandler executionHandler;

    private final Boolean useSSL;
    private final String serverName;
    private final int localPort;

    public static ChannelGroup AllChannels = new DefaultChannelGroup();

    public final Timer timer = new HashedWheelTimer();

    private final ConnectionTracker connectionTracker = new ConnectionTracker();

    static
    {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
    }

    private static final Logger logger = LoggerFactory.getLogger(NetworkServer.class);

    public NetworkServer(int localPort, String serverName, Boolean useSSL)
    {
        this.useSSL = useSSL;
        this.localPort = localPort;
        this.serverName = serverName;
        this.executionHandler = new ExecutionHandler(new RequestThreadPoolExecutor());
    }

    public void run(){

        logger.debug("Starting netty client connection server.");

        factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
        ServerBootstrap bootstrap = new ServerBootstrap(factory);

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new PipelineFactory(this, useSSL));

        /*bootstrap.setOption("child.sendBufferSize", 1048576);
        bootstrap.setOption("child.receiveBufferSize", 1048576);
        bootstrap.setOption("child.receiveBufferSizePredictorFactory", new AdaptiveReceiveBufferSizePredictorFactory());
        bootstrap.setOption("child.writeBufferLowWaterMark", 32 * 1024);
        bootstrap.setOption("child.writeBufferHighWaterMark", 64 * 1024);*/
        bootstrap.setOption("child.keepAlive", true);
        bootstrap.setOption("child.tcpNoDelay", true);

        bootstrap.bind(new InetSocketAddress(localPort));

    }

    public void stop(){
        close();
    }

    public void close(){
        AllChannels.unbind();
        AllChannels.close().awaitUninterruptibly(6000);

        connectionTracker.closeAll();

        factory.releaseExternalResources();
        factory = null;
    }

    private static class ConnectionTracker implements Tracker
    {
        //public final ChannelLocal<Connection> openedConnections = new ChannelLocal<Connection>(true);
        public final ChannelGroup allChannels = new DefaultChannelGroup();

        public void addConnection(Channel ch /*, Connection connection*/)
        {
            allChannels.add(ch);
            //openedConnections.set(ch, connection);
        }

        public void closeAll()
        {
            allChannels.close().awaitUninterruptibly();
        }
    }

    private static class PipelineFactory implements ChannelPipelineFactory
    {

        private final NetworkServer server;

        private final Boolean isSSL;

        public PipelineFactory(NetworkServer server, Boolean useSSL)
        {
            this.server = server;
            isSSL = useSSL;
        }

        public ChannelPipeline getPipeline() throws Exception
        {
            ChannelPipeline pipeline = Channels.pipeline();

            /*if(isSSL){
                SSLEngine engine =
                        SecureConnectionSslContextFactory.getServerContext().createSSLEngine();
                engine.setUseClientMode(false);

                pipeline.addLast("ssl", new SslHandler(engine));
            }*/

            pipeline.addLast("framer", new XMLFrameDecoder());

            pipeline.addLast("executor", server.executionHandler);
            pipeline.addLast("initiator", new ClientConnectionHandler(server.serverName));

            return pipeline;
        }
    }
}
