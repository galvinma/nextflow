package nextflow.cloud.azure

import groovy.transform.CompileStatic
import nextflow.cloud.azure.nio.AzFileSystemProvider
import nextflow.file.FileHelper
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

/**
 * Azure cloud plugin for Nextflow
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class AzurePlugin extends BasePlugin {

    AzurePlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    void start() {
        super.start()
        // register Azure file system
        FileHelper.getOrInstallProvider(AzFileSystemProvider)
    }
}
