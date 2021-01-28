package nextflow.cloud.azure.batch

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder

/**
 * Model the spec of Azure VM pool
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@Builder
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class VmPoolSpec {
    String poolId
    VmType vmType
}
