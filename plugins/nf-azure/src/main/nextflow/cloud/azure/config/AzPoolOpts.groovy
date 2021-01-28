/*
 * Copyright 2020, Microsoft Corp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.cloud.azure.config

import com.microsoft.azure.batch.protocol.models.OSType
import com.microsoft.azure.batch.protocol.models.VerificationType

/**
 * Model the setting of a VM pool
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AzPoolOpts {

    static public final String DEFAULT_PUBLISHER = "microsoft-azure-batch"
    static public final String DEFAULT_OFFER = "centos-container"
    static public final String DEFAULT_VM_TYPE = "STANDARD_A1"
    static public final OSType DEFAULT_OS_TYPE = OSType.LINUX

    String publisher
    String offer
    String vmType
    OSType osType = DEFAULT_OS_TYPE
    VerificationType verification = VerificationType.VERIFIED
    Integer vmCount = 1
    boolean autoScale

    AzPoolOpts(Map opts) {
        this.publisher = opts.publisher ?: DEFAULT_PUBLISHER
        this.offer = opts.offer ?: DEFAULT_OFFER
        this.vmType = opts.vmType ?: DEFAULT_VM_TYPE
        this.vmCount = opts.vmCount as Integer ?: 1
        this.autoScale = opts.autoScale as boolean
    }
}
