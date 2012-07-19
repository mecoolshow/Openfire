package org.jivesoftware.openfire.netty;


import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;


import java.util.ArrayList;
import java.util.List;


/**
 * Decodes an XML stream into XML Events.
 */
public class XMLFrameDecoder extends FrameDecoder {

    private final XMLLightweightParser reader;

    public XMLFrameDecoder() {
        super(true);

        reader = new XMLLightweightParser("UTF-8");
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {

        final List<String> result = new ArrayList<String>();

        reader.read(buffer.toByteBuffer());
        buffer.readerIndex(buffer.readableBytes());

        if (reader.areThereMsgs()) {

            for (String stanza : reader.getMsgs())
            {
                result.add(stanza);
            }
            return result;
        }
        else
        {
            return null;
        }
    }

}
