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
package nextflow.cloud.azure.batch

import java.nio.file.Path

import com.microsoft.azure.batch.protocol.models.TaskState
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.BashWrapperBuilder
import nextflow.processor.TaskBean
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
/**
 * Implements a task handler for Azure Batch service
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class AzBatchTaskHandler extends TaskHandler {

    AzBatchExecutor executor

    private TaskBean taskBean

    private Path exitFile

    private Path outputFile

    private Path errorFile

    private volatile AzTaskKey taskKey

    private volatile long timestamp

    private volatile TaskState taskState

    private String sasToken

    private Path remoteBinDir

    AzBatchTaskHandler(TaskRun task, AzBatchExecutor executor) {
        super(task)
        this.executor = executor
        this.taskBean = new TaskBean(task)
        this.outputFile = task.workDir.resolve(TaskRun.CMD_OUTFILE)
        this.errorFile = task.workDir.resolve(TaskRun.CMD_ERRFILE)
        this.exitFile = task.workDir.resolve(TaskRun.CMD_EXIT)
        this.remoteBinDir = executor.remoteBinDir
        this.sasToken = executor.sasToken
        validateConfiguration()
    }

    AzBatchService getBatchService() {
        return executor.batchService
    }

    void validateConfiguration() {
        if (!task.container) {
            throw new ProcessUnrecoverableException("No container image specified for process $task.name -- Either specify the container to use in the process definition or with 'process.container' value in your config")
        }
    }

    @Override
    void submit() {
        log.debug "[AZURE BATCH] Submitting task $task.name - work-dir=${task.workDirStr}"
        new BashWrapperBuilder(taskBean, new AzFileCopyStrategy(taskBean, sasToken, remoteBinDir)) {
            @Override
            protected boolean shouldUnstageOutputs() { return true }
        }.build()

        this.taskKey = batchService.submitTask(task, sasToken)
        log.debug "[AZURE BATCH] Submitted task $task.name with taskId=$taskKey"
        this.status = TaskStatus.SUBMITTED
    }

    @Override
    boolean checkIfRunning() {
        if( !taskKey || !isSubmitted() )
            return false
        final state = taskState0(taskKey)
        // note, include complete status otherwise it hangs if the task
        // completes before reaching this check
        final running = state==TaskState.RUNNING || state==TaskState.COMPLETED
        log.debug "[AZURE BATCH] Task status $task.name taskId=$taskKey; running=$running"
        if( running )
            this.status = TaskStatus.RUNNING
        return running
    }

    @Override
    boolean checkIfCompleted() {
        assert taskKey
        if( !isRunning() )
            return false
        final done = taskState0(taskKey)==TaskState.COMPLETED
        if( done ) {
            // finalize the task
            task.exitStatus = readExitFile()
            task.stdout = outputFile
            task.stderr = errorFile
            status = TaskStatus.COMPLETED
            deleteTask(taskKey, task)
            return true
        }
        return false
    }

    protected void deleteTask(AzTaskKey taskKey, TaskRun task) {
        if( !taskKey )
            return

        if( cleanupDisabled() )
            return

        if( !task.isSuccess() ) {
            // do not delete successfully executed pods for debugging purpose
            return
        }

        try {
            batchService.deleteTask(taskKey)
        }
        catch( Exception e ) {
            log.warn "Unable to cleanup batch task: $taskKey -- see the log file for details", e
        }
    }

    protected boolean cleanupDisabled() {
        return executor.config.batch().cleanup == false
    }

    /**
     * @return Retrieve the task status caching the result for at lest one second
     */
    protected TaskState taskState0(AzTaskKey key) {
        final now = System.currentTimeMillis()
        final delta =  now - timestamp;
        if( !taskState || delta >= 1_000) {
            def newState = batchService.getTask(key).state()
            log.debug "[AZURE BATCH] Task: $key state=$newState"
            if( newState ) {
                taskState = newState
                timestamp = now
            }
        }
        return taskState
    }

    protected int readExitFile() {
        try {
            exitFile.text as Integer
        }
        catch( Exception e ) {
            log.debug "[AZURE BATCH] Cannot read exitstatus for task: `$task.name` | ${e.message}"
            return Integer.MAX_VALUE
        }
    }

    @Override
    void kill() {
        if( !taskKey )
            return
        batchService.terminate(taskKey)
    }

}