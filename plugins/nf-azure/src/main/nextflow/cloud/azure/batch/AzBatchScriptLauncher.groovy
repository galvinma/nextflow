package nextflow.cloud.azure.batch


import nextflow.executor.BashWrapperBuilder
import nextflow.processor.TaskBean
/**
 * Custom bash wrapper builder for Azure batch tasks
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AzBatchScriptLauncher extends BashWrapperBuilder {

    AzBatchScriptLauncher(TaskBean bean, AzBatchExecutor executor) {
        super(bean, new AzFileCopyStrategy(bean, executor))
    }

    @Override
    protected boolean shouldUnstageOutputs() {
        return true
    }
}
