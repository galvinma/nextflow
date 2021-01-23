package nextflow.cloud.azure.nio

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel

import groovy.transform.CompileStatic

/**
 * Implements a {@link SeekableByteChannel} for a given {@link ReadableByteChannel}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class AzReadableByteChannel implements SeekableByteChannel {

    private long _position
    private ReadableByteChannel channel
    private long size

    AzReadableByteChannel(ReadableByteChannel channel, long size) {
        this.channel = channel
        this.size = size
    }

    @Override
    int read(ByteBuffer dst) throws IOException {
        final len = channel.read(dst)
        _position += len
        return len
    }

    @Override
    int write(ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    long position() throws IOException {
        return _position
    }

    @Override
    SeekableByteChannel position(long newPosition) throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    long size() throws IOException {
        return size
    }

    @Override
    SeekableByteChannel truncate(long dummy) throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    boolean isOpen() {
        return true
    }

    @Override
    void close() throws IOException {
        channel.close()
    }

}
