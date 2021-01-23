package nextflow.cloud.azure.nio

import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

import com.azure.storage.blob.BlobClient

/**
 * Implements {@link BasicFileAttributeView} for Azure stoarge blob
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AzFileAttributesView implements BasicFileAttributeView {

    private BlobClient client

    AzFileAttributesView( BlobClient client )  {
        this.client = client
    }

    @Override
    String name() {
        return 'basic'
    }

    @Override
    BasicFileAttributes readAttributes() throws IOException {
        return new AzFileAttributes(client)
    }

    /**
     * This API is implemented is not supported but instead of throwing an exception just do nothing
     * to not break the method {@link java.nio.file.CopyMoveHelper#copyToForeignTarget(java.nio.file.Path, java.nio.file.Path, java.nio.file.CopyOption...)}
     *
     * @param lastModifiedTime
     * @param lastAccessTime
     * @param createTime
     * @throws IOException
     */
    @Override
    void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        // TBD
    }

}
