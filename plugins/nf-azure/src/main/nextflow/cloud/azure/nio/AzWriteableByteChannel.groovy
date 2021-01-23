package nextflow.cloud.azure.nio

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.channels.WritableByteChannel

import groovy.transform.CompileStatic

/**
 * Implements a {@link SeekableByteChannel} for a given {@link java.nio.channels.WritableByteChannel}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class AzWriteableByteChannel implements SeekableByteChannel {

    private long _pos
    private WritableByteChannel writer

    AzWriteableByteChannel(WritableByteChannel channel) {
        this.writer = channel
    }

    @Override
    int read(ByteBuffer dst) throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    int write(ByteBuffer src) throws IOException {
        def len = writer.write(src)
        _pos += len
        return len
    }

    @Override
    long position() throws IOException {
        return _pos
    }

    @Override
    SeekableByteChannel position(long newPosition) throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    long size() throws IOException {
        return _pos
    }

    @Override
    SeekableByteChannel truncate(long size) throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    boolean isOpen() {
        return true
    }

    @Override
    void close() throws IOException {
        writer.close()
    }

}
