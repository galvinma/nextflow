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

import nextflow.Session
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AzureConfigTest extends Specification {

    def 'should get azure storage options' () {

        given:
        def KEY = 'xyz1343'
        def NAME = 'container-foo'
        def STORES = 'this-that'
        def SAS = 'foo'
        and:
        def session = Mock(Session) {
            getConfig() >> [ azure:
                                [storage:[
                                    accountKey: KEY,
                                    accountName: NAME,
                                    fileStores: STORES,
                                    sasToken: SAS
                                ] ]]
        }
        
        when:
        def cfg = AzConfig.getConfig(session)
        then:
        cfg.storage().accountKey == KEY
        cfg.storage().accountName == NAME
        cfg.storage().sasToken == SAS
        and:
        cfg.storage().getEnv() == [AZURE_STORAGE_ACCOUNT_KEY: KEY,
                                   AZURE_STORAGE_ACCOUNT_NAME: NAME,
                                   AZURE_STORAGE_SAS_TOKEN: SAS ]
        
        and:
        cfg.batch().accountKey == null
        cfg.batch().accountName == null
        cfg.batch().endpoint == null
        cfg.batch().cleanup == null
        cfg.batch().location == null
        cfg.batch().pool().vmType == 'STANDARD_A1'
        cfg.batch().pool().vmCount == 1
        cfg.batch().pool().autoScale == false
    }

    def 'should get azure batch options' () {

        given:
        def KEY = 'xyz1343'
        def NAME = 'container-foo'
        def ENDPOINT = 'http://foo/bar'
        def LOCATION = 'europenorth'
        and:
        def session = Mock(Session) {
            getConfig() >> [ azure:
                                     [batch:[
                                             accountKey: KEY,
                                             accountName: NAME,
                                             endpoint: ENDPOINT,
                                             location: LOCATION,
                                             cleanup: true,
                                             pool: [vmType: 'Foo_A1', autoScale: true]
                                     ]] ]
        }

        when:
        def cfg = AzConfig.getConfig(session)
        then:
        cfg.batch().accountKey == KEY
        cfg.batch().accountName == NAME
        cfg.batch().endpoint == ENDPOINT
        cfg.batch().location == LOCATION
        cfg.batch().cleanup == true
        and:
        cfg.batch().pool().vmType == 'Foo_A1'
        cfg.batch().pool().autoScale == true
        and:
        cfg.storage().accountKey == null
        cfg.storage().accountName == null
        cfg.storage().sasToken == null
    }

    def 'should get azure batch endpoint from account and location' () {

        given:
        def KEY = 'xyz1343'
        def NAME = 'nfbucket'
        def LOCATION = 'europenorth'
        and:
        def session = Mock(Session) {
            getConfig() >> [ azure:
                                     [batch:[
                                             accountKey: KEY,
                                             accountName: NAME,
                                             location: LOCATION,
                                             cleanup: true ]] ]
        }

        when:
        def cfg = AzConfig.getConfig(session)
        then:
        cfg.batch().accountKey == KEY
        cfg.batch().accountName == NAME
        cfg.batch().endpoint == 'https://nfbucket.europenorth.batch.azure.com'
        cfg.batch().location == LOCATION
    }
}