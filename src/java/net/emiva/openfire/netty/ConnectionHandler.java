/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.emiva.openfire.netty;

import org.dom4j.io.XMPPPacketReader;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;

import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jivesoftware.openfire.net.MXParser;

import org.jivesoftware.openfire.net.StanzaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * A ConnectionHandler is responsible for creating new sessions, destroying sessions and delivering
 * received XML stanzas to the proper StanzaHandler.
 *
 * @author Gaston Dombiak
 */
public abstract class ConnectionHandler extends IdleStateAwareChannelHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionHandler.class);

    protected XMPPPacketReader parser = new XMPPPacketReader();
    protected StanzaHandler handler;
    protected NIOConnection connection;

    protected String serverName;

    /**
     * Reuse the same factory for all the connections.
     */
    private static XmlPullParserFactory factory = null;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            logger.error("Error creating a parser factory", e);
        }
    }

    protected ConnectionHandler(String serverName) {
        this.serverName = serverName;
    }

    @Override
    public void channelOpen(final ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        parser.setXPPFactory(factory);
        connection = createNIOConnection(e.getChannel());
        handler = createStanzaHandler(connection);
    }


    @Override
    public void closeRequested(final ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (e.getChannel().isConnected() && e.getChannel().isWritable()) {
            e.getChannel().write(ChannelBuffers.EMPTY_BUFFER);
        }
        super.closeRequested(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {

        this.logger.error("Client exception caught: ", e.getCause());
        connection.close();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
            throws Exception {

        try {
            handler.process((String) e.getMessage(), parser);
        } catch (Exception ex) {
            logger.error("Closing connection due to error while processing message: " + e.getMessage(), ex.getCause());
            connection.close();
        }
    }

    abstract NIOConnection createNIOConnection(Channel session);

    abstract StanzaHandler createStanzaHandler(NIOConnection connection);

    /**
     * Returns the max number of seconds a connection can be idle (both ways) before
     * being closed.<p>
     *
     * @return the max number of seconds a connection can be idle.
     */
    abstract int getMaxIdleTime();

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isConnected() && ch.isWritable()) {
            ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
