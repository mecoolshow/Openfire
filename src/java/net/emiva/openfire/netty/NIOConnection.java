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

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.security.cert.Certificate;


import org.dom4j.io.OutputFormat;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.util.CharsetUtil;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.auth.UnauthorizedException;

import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

import org.jboss.netty.channel.Channel;

/**
 * Implementation of {@link Connection} inteface specific for NIO connections when using
 * the MINA framework.<p>
 *
 * MINA project can be found at <a href="http://mina.apache.org">here</a>.
 *
 * @author Gaston Dombiak
 */
public class NIOConnection implements Connection {

    private static final Logger logger = LoggerFactory.getLogger(NIOConnection.class);

    public static final String CHARSET = "UTF-8";

    private LocalSession session;
    private Channel connection;

    private ConnectionCloseListener closeListener;

    /**
     * Deliverer to use when the connection is closed or was closed when delivering
     * a packet.
     */
    private PacketDeliverer backupDeliverer;

    private int majorVersion = 1;
    private int minorVersion = 0;
    private String language = null;

    private TLSPolicy tlsPolicy = TLSPolicy.optional;
    private boolean usingSelfSignedCertificate;

    /**
     * Compression policy currently in use for this connection.
     */
    private CompressionPolicy compressionPolicy = CompressionPolicy.disabled;

    /**
     * Flag that specifies if the connection should be considered closed. Closing a NIO connection
     * is an asynch operation so instead of waiting for the connection to be actually closed just
     * keep this flag to avoid using the connection between #close was used and the socket is actually
     * closed.
     */
    private boolean closed;


    public NIOConnection(Channel session, PacketDeliverer packetDeliverer) {
        this.connection = session;

        this.connection.getCloseFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                notifyCloseListeners();
            }
        });

        this.backupDeliverer = packetDeliverer;
        closed = false;
    }

    public boolean validate() {
        if (isClosed()) {
            return false;
        }
        deliverRawText(" ");
        return !isClosed();
    }

    public void notifyCloseListeners() {
        if (closeListener != null) {
            try {
                closeListener.onConnectionClose(session);
            } catch (Exception e) {
                logger.error("Error notifying listener: " + closeListener, e);
            }
        }
    }

    public void registerCloseListener(ConnectionCloseListener listener, Object ignore) {
        if (closeListener != null) {
            throw new IllegalStateException("Close listener already configured");
        }
        if (isClosed()) {
            listener.onConnectionClose(session);
        }
        else {
            closeListener = listener;
        }
    }

    public void removeCloseListener(ConnectionCloseListener listener) {
        if (closeListener == listener) {
            closeListener = null;
        }
    }

    public byte[] getAddress() throws UnknownHostException {
        return ((InetSocketAddress) connection.getRemoteAddress()).getAddress().getAddress();
    }

    public String getHostAddress() throws UnknownHostException {
        return ((InetSocketAddress) connection.getRemoteAddress()).getAddress().getHostAddress();
    }

    public String getHostName() throws UnknownHostException {
        return ((InetSocketAddress) connection.getRemoteAddress()).getAddress().getHostName();
    }

    public void setUsingSelfSignedCertificate(boolean isSelfSigned) {
        this.usingSelfSignedCertificate = isSelfSigned;
    }

    public boolean isUsingSelfSignedCertificate() {
        return usingSelfSignedCertificate;
    }

    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    public void close() {
        boolean closedSuccessfully = false;
        synchronized (this) {
            if (!isClosed()) {
                try {
                    deliverRawText("</stream:stream>", false);
                } catch (Exception e) {
                    // Ignore
                }
                if (session != null) {
                    session.setStatus(Session.STATUS_CLOSED);
                }
                connection.close();
                closed = true;
                closedSuccessfully = true;
            }
        }
        if (closedSuccessfully) {
            notifyCloseListeners();
        }
    }

    public void systemShutdown() {
        deliverRawText("<stream:error><system-shutdown " +
                "xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>");
        close();
    }

    public void init(LocalSession owner) {
        session = owner;
    }

    public boolean isClosed() {
        if (session == null) {
            return closed;
        }
        return session.getStatus() == Session.STATUS_CLOSED;
    }

    public boolean isSecure() {
        return connection.getPipeline().getNames().contains("tls");
    }

    public void deliver(Packet packet) throws UnauthorizedException {
        if (isClosed()) {
            backupDeliverer.deliver(packet);
        }
        else {
            ChannelBuffer buffer = ChannelBuffers.dynamicBuffer(4096);


            boolean errorDelivering = false;
            try {
                XMLWriter xmlSerializer =
                        new XMLWriter(new ChannelBufferWriter(buffer, CharsetUtil.UTF_8), new OutputFormat());
                xmlSerializer.write(packet.getElement());
                xmlSerializer.flush();

                connection.write(buffer);
            }
            catch (Exception e) {
                logger.debug("NIOConnection: Error delivering packet" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            if (errorDelivering) {
                close();
                // Retry sending the packet again. Most probably if the packet is a
                // Message it will be stored offline
                backupDeliverer.deliver(packet);
            }
            else {
                session.incrementServerPacketCount();
            }
        }
    }

    public void deliverRawText(String text) {
        // Deliver the packet in asynchronous mode
        deliverRawText(text, true);
    }

    private void deliverRawText(String text, boolean asynchronous) {
        if (!isClosed()) {

            boolean errorDelivering = false;


            try {
                if (asynchronous) {
                    connection.write(ChannelBuffers.copiedBuffer(text.getBytes(CHARSET)));
                }
                else {
                    // Send stanza and wait for ACK (using a 2 seconds default timeout)

                    connection.write(ChannelBuffers.copiedBuffer(text.getBytes(CHARSET))).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if(!future.isSuccess())
                            {
                                logger.warn("No ACK was received when sending stanza to: " + this.toString());
                            }
                        }
                    });
                }
            }
            catch (Exception e) {
                logger.debug("NIOConnection: Error delivering raw text" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            // Close the connection if delivering text fails and we are already not closing the connection
            if (errorDelivering && asynchronous) {
                close();
            }
        }
    }

    /* Implement later */
    public void startTLS(boolean clientMode, String remoteServer, ClientAuth authentication) throws Exception {

        if (!clientMode) {
            // Indicate the client that the server is ready to negotiate TLS
            deliverRawText("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
        }
    }

    /* Implement later */
    public void addCompression() {}

    /* Implement later */
    public void startCompression() {}

    /* Implement later */
    public boolean isFlashClient() {
        return false;
    }

    /* Implement later */
    public void setFlashClient(boolean flashClient) {}

    /* Implement later */
    public Certificate[] getLocalCertificates() {
        return new Certificate[0];
    }

    /* Implement later */
    public Certificate[] getPeerCertificates() {
        return new Certificate[0];
    }

    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    public void setXMPPVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanaguage(String language) {
        this.language = language;
    }

    public boolean isCompressed() {
        return connection.getPipeline().getNames().contains("compression");
    }

    public CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    public void setCompressionPolicy(CompressionPolicy compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
    }

    public TLSPolicy getTlsPolicy() {
        return tlsPolicy;
    }

    public void setTlsPolicy(TLSPolicy tlsPolicy) {
        this.tlsPolicy = tlsPolicy;
    }

    @Override
    public String toString() {
        return super.toString() + " Netty Session: " + connection;
    }

    private static class ThreadLocalEncoder extends ThreadLocal<CharsetEncoder> {

        @Override
        protected CharsetEncoder initialValue() {
            return Charset.forName(CHARSET).newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
    }
}
