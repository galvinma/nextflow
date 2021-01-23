package nextflow.cloud.azure.batch

import java.nio.file.Path
import java.time.OffsetDateTime

import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.sas.BlobSasPermission
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues
import groovy.transform.CompileStatic
import nextflow.cloud.azure.nio.AzPath
import nextflow.util.Duration
/**
 * Azure helper functions
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class AzHelper {

    static private AzPath az0(Path path){
        if( path !instanceof AzPath )
            throw new IllegalArgumentException("Not a valid Azure path: $path [${path?.getClass()?.getName()}]")
        return (AzPath)path
    }

    static String toHttpUrl(Path path, String sas=null) {
        def url = az0(path).blobClient().getBlobUrl()
        url = URLDecoder.decode(url, 'UTF-8').stripEnd('/')
        return !sas ? url : "${url}?${sas}"
    }

    static String toContainerUrl(Path path, String sas) {
        def url = az0(path).containerClient().getBlobContainerUrl()
        url = URLDecoder.decode(url, 'UTF-8').stripEnd('/')
        return !sas ? url : "${url}?${sas}"
    }

    static String generateSas(Path path, Duration duration, String perms) {
        generateSas(az0(path).blobClient(), duration, perms)
    }

    static String generateSas(BlobClient client, Duration duration, String perms) {
        final offset = OffsetDateTime
                .now()
                .plusSeconds(duration.seconds)

        return client
                .generateSas(new BlobServiceSasSignatureValues(offset, BlobSasPermission.parse(perms)))
    }

}
