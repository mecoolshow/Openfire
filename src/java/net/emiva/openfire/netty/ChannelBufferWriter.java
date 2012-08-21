package net.emiva.openfire.netty;



import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;


public class ChannelBufferWriter extends Writer {

    private ChannelBuffer channelBuffer;
    private Charset charset;

    public ChannelBufferWriter(ChannelBuffer byteBuffer, Charset charset) {
        this.charset = charset;
        this.channelBuffer = byteBuffer;
    }

    @Override
    public void write(char array[], int offset, int length) throws IOException {
        channelBuffer.writeBytes(ChannelBuffers.copiedBuffer(array, offset, length, charset));
    }

    @Override
    public void flush() throws IOException {
        // Ignore
    }

    @Override
    public void close() throws IOException {
        // Ignore
    }
}
