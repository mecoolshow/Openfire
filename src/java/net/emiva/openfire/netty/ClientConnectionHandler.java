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

import org.jboss.netty.channel.Channel;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.net.ClientStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.IQ.Type;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class ClientConnectionHandler extends ConnectionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientConnectionHandler.class);

    public ClientConnectionHandler(String serverName) {
        super(serverName);
    }

    @Override
    NIOConnection createNIOConnection(Channel session) {
        return new NIOConnection(session, XMPPServer.getInstance().getPacketDeliverer());
    }

    @Override
    StanzaHandler createStanzaHandler(NIOConnection connection) {
        return new ClientStanzaHandler(XMPPServer.getInstance().getPacketRouter(), serverName, connection);
    }

    @Override
    int getMaxIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.client.idle", 6 * 60 * 1000) / 1000;
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e) throws Exception {
        final boolean doPing = JiveGlobals.getBooleanProperty("xmpp.client.idle.ping", true);
        if (doPing) {
            final JID entity = handler.getAddress();

            if (entity != null) {
                // Ping the connection to see if it is alive.
                final IQ pingRequest = new IQ(Type.get);
                pingRequest.setChildElement("ping",
                        IQPingHandler.NAMESPACE);
                pingRequest.setFrom(serverName);
                pingRequest.setTo(entity);


                logger.debug("ConnectionHandler: Pinging connection that has been idle: " + connection);

                connection.deliver(pingRequest);
            }
        }
    }
}
