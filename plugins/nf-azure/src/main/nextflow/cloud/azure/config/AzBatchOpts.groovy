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

import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class AzBatchOpts {

    String accountName
    String accountKey
    String endpoint
    String location
    Boolean cleanup
    Boolean autoPool

    Map<String,AzPoolOpts> pools

    AzBatchOpts(Map config) {
        assert config!=null
        accountName = config.accountName
        accountKey = config.accountKey
        endpoint = config.endpoint
        location = config.location
        cleanup = config.cleanup
        autoPool = config.autoPool
        pools = parsePools(config.pools instanceof Map ? config.pools as Map<String,Map> : Collections.<String,Map>emptyMap())
    }

    static Map<String,AzPoolOpts> parsePools(Map<String,Map> pools) {
        final result = new LinkedHashMap<String,AzPoolOpts>()
        for( Map.Entry<String,Map> entry : pools ) {
            result[entry.key] = new AzPoolOpts( entry.value )
        }
        if( !result.keySet().contains('auto') )
            result.put('auto', new AzPoolOpts())
        return result
    }

    AzPoolOpts pool(String name) {
        return pools.get(name)
    }

    AzPoolOpts autoPoolOpts() {
        pool('auto')
    }

    String toString() {
        "endpoint=$endpoint; account-name=$accountName; account-key=${accountKey?.redact()}"
    }

    String getEndpoint() {
        if( endpoint )
            return endpoint
        if( accountName && location )
            return "https://${accountName}.${location}.batch.azure.com"
        return null
    }
}
