# Azure plugin for Nextflow 

This plugin implements the support for Azure Blob storage as fie system 
provider (via JSR203 interface) and Azure Batch executor  for Nextflow 


## Todo 

* Currently, the Blob storage service uses NettyHttpClient and Batch service 
uses OkHttp client, duplicating the number of required libraries. In principle 
the Blob service can use OkHttp, adding the following deps, however using that
Nextflow hangs during the shutdown, apparently because the connection pool used 
by the blob service is not closed timely. 

        compile('com.azure:azure-storage-blob:12.9.0') {
            exclude group: 'org.slf4j', module: 'slf4j-api'
            exclude group: 'com.azure', module: 'azure-core-http-netty'
        }
        compile('com.azure:azure-core-http-okhttp:1.3.3') {
            exclude group: 'org.slf4j', module: 'slf4j-api'
        }

* Allow accessing the Blob service via SAS token instead of account key 
* Remove invalid directory from .command.run PATH for project having `bin/` folder  

### Links
* https://github.com/Azure/azure-sdk-for-java/wiki
* https://github.com/Azure/azure-sdk-for-java/tree/master/sdk/storage/azure-storage-blob-nio
* https://github.com/Azure/azure-sdk-for-java/blob/master/sdk/storage/azure-storage-blob-nio/src/samples/java/com/azure/storage/blob/nio/ReadmeSamples.java

