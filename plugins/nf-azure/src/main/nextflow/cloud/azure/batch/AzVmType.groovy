package nextflow.cloud.azure.batch

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import nextflow.util.MemoryUnit

/**
 * Model the size of a Azure VM
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class AzVmType {
    String name
    Integer maxDataDiskCount
    MemoryUnit memory
    Integer numberOfCores
    MemoryUnit osDiskSize
    MemoryUnit resourceDiskSize

    AzVmType() {}

    AzVmType(Map map) {
        this.name = map.name
        this.maxDataDiskCount = map.maxDataDiskCount as Integer
        this.memory = map.memoryInMb ? MemoryUnit.of( "$map.memoryInMb MB" ) : null
        this.numberOfCores = map.numberOfCores as Integer
        this.osDiskSize = map.osDiskSizeInMb ? MemoryUnit.of( "$map.osDiskSizeInMb MB" ) : null
        this.resourceDiskSize = map.resourceDiskSizeInMb ? MemoryUnit.of( "$map.resourceDiskSizeInMb MB" ) : null
    }
}
