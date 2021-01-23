package nextflow.cloud.azure.nio

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Path
import java.nio.file.Paths

import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlobStorageException
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
trait AzBaseSpec {

    static private Random RND = new Random()

    abstract BlobServiceClient getStorageClient()

    private String rnd() {
        Integer.toHexString(RND.nextInt(Integer.MAX_VALUE))
    }

    String createBucket(String containerName) {
        storageClient.createBlobContainer(containerName)
        return containerName
    }

    String createBucket() {
        createBucket(getRndBucketName())
    }

    String getRndBucketName() {
        return "nf-az-blob-${UUID.randomUUID()}"
    }

    def createObject(String path, String content) {
        createObject(Paths.get(path), content)
    }

    def createObject(Path path, String content) {
        if( path.nameCount<=1 )
            throw new IllegalArgumentException("There should be at least one dir level: $path")
        final containerName = path.subpath(0,1).toString()
        final blobName = path.subpath(1, path.nameCount).toString()
        final blob = storageClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName)
                .getAppendBlobClient()

        blob.create()

        blob
                .blobOutputStream
                .withStream { it.write(content.bytes) }
    }

    def createDirectory(String path) {

        final containerName = path.tokenize('/')[0]
        final blobName = path.tokenize('/')[1..-1].join('/') + '/'
        storageClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName)
                .appendBlobClient
                .create()
    }

    def deleteObject(String path) {
        final containerName = path.tokenize('/')[0]
        final blobName = path.tokenize('/')[1..-1].join('/') + '/'

        storageClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName)
                .delete()
    }

    def deleteBucket(Path path) {
        assert path.nameCount == 1
        deleteBucket(path.getName(0).toString())
    }

    def deleteBucket(String bucketName) {
        if( !bucketName )
            return

        storageClient.deleteBlobContainer(bucketName)
    }

    boolean existsPath(String path) {
        existsPath(Paths.get(path))
    }

    boolean existsPath(Path path) {
        if( path.nameCount == 1 ) {
            try {
                storageClient
                        .getBlobContainerClient(path.getName(0).toString())
                        .getProperties()
                return true
            }
            catch (BlobStorageException e) {
                if( e.statusCode == 404 )
                    return false
                throw e
            }
        }

        final containerName = path.subpath(0,1).toString()
        final blobName = path.subpath(1,path.nameCount).toString()
        try {
            storageClient.getBlobContainerClient(containerName)
                .getBlobClient(blobName)
                .getProperties()
            return true
        }
        catch (BlobStorageException e) {
            if( e.statusCode == 404 )
                return false
            throw e
        }
    }

    String readObject(String path) {
        readObject(Paths.get(path))
    }

    String readObject(Path path) {
        final containerName = path.subpath(0,1).toString()
        final blobName = path.subpath(1,path.nameCount).toString()
        try {
            return storageClient.getBlobContainerClient(containerName)
                    .getBlobClient(blobName)
                    .openInputStream()
                    .text

        }
        catch (BlobStorageException e) {
            throw e
        }
    }


    String randomText(int size) {
        def result = new StringBuilder()
        while( result.size() < size ) {
            result << UUID.randomUUID().toString() << '\n'
        }
        return result.toString()
    }

    String readChannel(SeekableByteChannel sbc, int buffLen )  {
        def buffer = new ByteArrayOutputStream()
        ByteBuffer bf = ByteBuffer.allocate(buffLen)
        while((sbc.read(bf))>0) {
            bf.flip();
            buffer.write(bf.array(), 0, bf.limit())
            bf.clear();
        }

        buffer.toString()
    }

    void writeChannel( SeekableByteChannel channel, String content, int buffLen ) {

        def bytes = content.getBytes()
        ByteBuffer buf = ByteBuffer.allocate(buffLen);
        int i=0
        while( i < bytes.size()) {

            def len = Math.min(buffLen, bytes.size()-i);
            buf.clear();
            buf.put(bytes, i, len);
            buf.flip();
            channel.write(buf);

            i += len
        }

    }

}
