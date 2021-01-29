package nextflow.cloud.azure.batch

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import nextflow.cloud.azure.config.AzPoolOpts

/**
 * Model the spec of Azure VM pool
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@Builder
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class AzVmPoolSpec {
    String poolId
    AzVmType vmType
    AzPoolOpts opts
}
