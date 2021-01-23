package nextflow.cloud.azure.file

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.cloud.azure.nio.AzPath
import nextflow.file.FileHelper
import nextflow.util.SerializerRegistrant

/**
 * Implements Serializer for {@link AzPath} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class AzPathSerializer extends Serializer<AzPath> implements SerializerRegistrant {

    @Override
    void write(Kryo kryo, Output output, AzPath path) {
        log.trace "Azure Blob storage path serialisation > path=$path"
        output.writeString(path.toUriString())
    }

    @Override
    AzPath read(Kryo kryo, Input input, Class<AzPath> type) {
        final path = input.readString()
        log.trace "Azure Blob storage path > path=$path"
        return (AzPath)FileHelper.asPath(path)
    }

    @Override
    void register(Map<Class, Object> serializers) {
        serializers.put(AzPath, AzPathSerializer)
    }
}
