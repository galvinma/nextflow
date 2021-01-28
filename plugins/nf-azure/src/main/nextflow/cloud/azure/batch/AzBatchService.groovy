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

import java.math.RoundingMode
import java.nio.file.Path
import java.time.Instant

import com.microsoft.azure.batch.BatchClient
import com.microsoft.azure.batch.auth.BatchSharedKeyCredentials
import com.microsoft.azure.batch.protocol.models.CloudTask
import com.microsoft.azure.batch.protocol.models.ContainerConfiguration
import com.microsoft.azure.batch.protocol.models.ImageInformation
import com.microsoft.azure.batch.protocol.models.OutputFile
import com.microsoft.azure.batch.protocol.models.OutputFileBlobContainerDestination
import com.microsoft.azure.batch.protocol.models.OutputFileDestination
import com.microsoft.azure.batch.protocol.models.OutputFileUploadCondition
import com.microsoft.azure.batch.protocol.models.OutputFileUploadOptions
import com.microsoft.azure.batch.protocol.models.PoolAddParameter
import com.microsoft.azure.batch.protocol.models.PoolInformation
import com.microsoft.azure.batch.protocol.models.ResourceFile
import com.microsoft.azure.batch.protocol.models.TaskAddParameter
import com.microsoft.azure.batch.protocol.models.TaskContainerSettings
import com.microsoft.azure.batch.protocol.models.VirtualMachineConfiguration
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.Session
import nextflow.cloud.azure.config.AzConfig
import nextflow.cloud.azure.config.AzPoolOpts
import nextflow.cloud.azure.nio.AzPath
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.util.MemoryUnit
import nextflow.util.MustacheTemplateEngine
import org.joda.time.Period

/**
 * Implements Azure Batch operations for Nextflow executor
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class AzBatchService implements Closeable {

    static private final long _1GB = 1 << 30

    static final private Map<String,VmPoolSpec> allPools = new HashMap<>(50)

    AzPoolOpts poolOpts

    AzConfig config

    Map<TaskProcessor,String> allJobIds = new HashMap<>(50)

    AzBatchService(AzBatchExecutor executor) {
        assert executor
        this.config = executor.config
        this.poolOpts = config.batch().pool()
    }

    @Memoized
    protected BatchClient getClient() {
        createBatchClient()
    }

    /**
     * @return The available location codes
     */
    @Memoized
    List<String> listLocationNames() {
        // this has been generated with `az account list-locations` cli tool
        // it needs to be replaced by a proper API call
        final json = AzBatchService.class.getResourceAsStream('/nextflow/cloud/azure/az-locations.json')
        if( !json )
            throw new IllegalArgumentException("Unable to fetch Azure locations")
        final locs = (new JsonSlurper().parse(json) as List<Map>)
        return locs.collect {  (String)it.name }
    }

    @Memoized
    private List<Map> listAllVms(String location) {
        if( !location )
            throw new IllegalArgumentException("Missing Azure location parameter")
        final json = AzBatchService.class.getResourceAsStream("/nextflow/cloud/azure/vm-list-size-${location}.json")
        if( !json ) {
            log.warn "Unable to find Azure VM names for location: $location"
            return Collections.emptyList()
        }

        return (List<Map>) new JsonSlurper().parse(json)
    }

    @Memoized
    List<String> listVmNames(String location) {
        return listAllVms(location).collect { it.name as String }
    }

    VmType guessBestVm(String location, int cpus, MemoryUnit mem, String family) {
        if( !family.contains('*') && !family.contains('?') )
            return findBestVm(location, cpus, mem, family)

        // well this is a quite heuristic tentative to find a bigger instance to accommodate more tasks
        VmType result=null
        if( cpus<=4 ) {
            result = findBestVm(location, cpus*4, mem!=null ? mem*4 : null, family)
            if( !result )
                result = findBestVm(location, cpus*2, mem!=null ? mem*2 : null, family)
        }
        else if( cpus <=8 ) {
            result = findBestVm(location, cpus*2, mem!=null ? mem*2 : null, family)
        }
        if( !result )
            result = findBestVm(location, cpus, mem, family)
        return result
    }

    VmType findBestVm(String location, int cpus, MemoryUnit mem, String family) {
        def all = listAllVms(location)
        def scores = new TreeMap<Double,String>()
        for( Map entry : all ) {
            if( !matchType(family, entry.name as String) )
                continue
            def score = computeScore(cpus, mem, entry)
            if( score != null )
                scores.put(score, entry.name as String)
        }
        return getVmSize(location, scores.firstEntry().value)
    }

    protected boolean matchType(String family, String vmType) {
        if( !family )
            return true
        if( family.contains('*') )
            family = family.toLowerCase().replaceAll(/\*/,'.*')
        else if( family.contains('?') )
            family = family.toLowerCase().replaceAll(/\?/,'.{1}')

        return vmType =~ /(?i)^${family}$/
    }
    
    protected Double computeScore(int cpus, MemoryUnit mem, Map entry) {
        def vmCores = entry.numberOfCores as int
        double vmMemGb = (entry.memoryInMb as int) /1024

        if( cpus > vmCores ) {
            return null
        }

        int cpusDelta = cpus-vmCores
        double score = cpusDelta * cpusDelta
        if( mem && vmMemGb ) {
            double memGb = mem.toMega()/1024
            if( memGb > vmMemGb )
                return null
            double memDelta = memGb - vmMemGb
            score += memDelta*memDelta
        }

        return Math.sqrt(score);
    }

    @Memoized
    VmType getVmSize(String location, String vmName) {
        def vm = listAllVms(location).find { vmName.equalsIgnoreCase(it.name?.toString()) }
        if( !vm )
            throw new IllegalArgumentException("Unable to find size for VM name '$vmName' and location '$location'")

        new VmType(vm)
    }

    protected int computeSlots(int cpus, MemoryUnit mem, int vmCpus, MemoryUnit vmMem) {
        //  cpus requested should not exceed max cpus avail
        final cpuSlots = Math.min(cpus, vmCpus) as int
        if( !mem || !vmMem )
            return cpuSlots
        //  mem requested should not exceed max mem avail
        final vmMemGb = vmMem.mega /_1GB as float
        final memGb = mem.mega /_1GB as float
        final mem0 = Math.min(memGb, vmMemGb)
        return Math.max(cpuSlots, memSlots(mem0, vmMemGb, vmCpus))
    }

    protected int computeSlots( TaskRun task, VmPoolSpec pool) {
        computeSlots(
                task.config.getCpus(),
                task.config.getMemory(),
                pool.vmType.numberOfCores,
                pool.vmType.memory )
    }


    protected int memSlots(float memGb, float vmMemGb, int vmCpus) {
        BigDecimal result = memGb / (vmMemGb / vmCpus)
        result.setScale(0, RoundingMode.UP).intValue()
    }

    protected BatchClient createBatchClient() {
        log.debug "[AZURE BATCH] Executor options=${config.batch()}"
        // Create batch client
        if( !config.batch().endpoint )
            throw new IllegalArgumentException("Missing Azure Batch endpoint -- Specify it in the nextflow.config file using the setting 'azure.batch.endpoint'")
        if( !config.batch().accountName )
            throw new IllegalArgumentException("Missing Azure Batch account name -- Specify it in the nextflow.config file using the setting 'azure.batch.accountName'")
        if( !config.batch().accountKey )
            throw new IllegalArgumentException("Missing Azure Batch account key -- Specify it in the nextflow.config file using the setting 'azure.batch.accountKet'")

        final cred = new BatchSharedKeyCredentials(config.batch().endpoint, config.batch().accountName, config.batch().accountKey)
        final client = BatchClient.open(cred)
        final sess = Global.session as Session
        sess.onShutdown { client.protocolLayer().restClient().close() }
        return client
    }


    AzTaskKey submitTask(TaskRun task, String sas) {
        final poolId = getOrCreatePool(task)
        final jobId = getOrCreateJob(poolId, task)
        runTask(poolId, jobId, task, sas)
    }

    CloudTask getTask(AzTaskKey key) {
        return client.taskOperations().getTask(key.jobId, key.taskId)
    }

    void terminate(AzTaskKey key) {
        client.taskOperations().terminateTask(key.jobId, key.taskId)
    }

    synchronized String getOrCreateJob(String poolId, TaskRun task) {
        final mapKey = task.processor
        if( allJobIds.containsKey(mapKey)) {
            return allJobIds[mapKey]
        }
        // create a batch job
        final jobId = makeJobId(task) + '-' + Random.newInstance().nextInt(5000)
        final poolInfo = new PoolInformation()
                            .withPoolId(poolId)
        client
            .jobOperations()
            .createJob(jobId, poolInfo)
        // add to the map
        allJobIds[mapKey] = jobId
        return jobId
    }

    String makeJobId(TaskRun task) {
        def name = task
                .processor
                .name.trim().replaceAll(/[^a-zA-Z0-9-_]+/,'_')
        return "nf-job-$name"
    }

    AzTaskKey runTask(String poolId, String jobId, TaskRun task, String sas) {
        assert poolId, 'Missing Azure Batch poolId argument'
        assert jobId, 'Missing Azure Batch jobId argument'
        assert task, 'Missing Azure Batch task argument'
        assert sas, 'Missing Azure Batch SAS argument'

        final container = task.config.container as String
        if( !container )
            throw new IllegalArgumentException("Missing container image for process: $task.name")

        final taskId = "nf-${task.hash.toString()}"
        final containerOpts = new TaskContainerSettings()
                .withImageName(container)
                // mount host certificates otherwise `azcopy fails
                //.withContainerRunOptions('-v /etc/ssl/certs:/etc/ssl/certs:ro')
                .withContainerRunOptions('-u root -v /usr/bin/docker:/usr/bin/docker -v /var/run/docker.sock:/var/run/docker.sock')

        final pool = allPools.get(poolId)
        if( !pool )
            throw new IllegalStateException("Missing Azure Batch pool spec with id: $poolId")

        final slots = computeSlots(task, pool)
        log.debug "[AZURE BATCH] Submitting task: $taskId, cpus=${task.config.getCpus()}, mem=${task.config.getMemory()?:'-'}, slots: $slots"

        final taskToAdd = new TaskAddParameter()
                .withId(taskId)
                .withContainerSettings(containerOpts)
                .withCommandLine("bash ${TaskRun.CMD_RUN}")
                .withResourceFiles(resourceFileUrls(task,sas))
                .withOutputFiles(outputFileUrls(task, sas))
                .withRequiredSlots(slots)

        client.taskOperations().createTask(jobId, taskToAdd)
        return new AzTaskKey(jobId, taskId)
    }

    protected List<ResourceFile> resourceFileUrls(TaskRun task, String sas) {
        final cmdRun = (AzPath) task.workDir.resolve(TaskRun.CMD_RUN)
        final cmdScript = (AzPath) task.workDir.resolve(TaskRun.CMD_SCRIPT)

        final resFiles = new ArrayList(10)

        resFiles << new ResourceFile()
                .withHttpUrl(AzHelper.toHttpUrl(cmdRun, sas))
                .withFilePath(TaskRun.CMD_RUN)

        resFiles << new ResourceFile()
                .withHttpUrl(AzHelper.toHttpUrl(cmdScript, sas))
                .withFilePath(TaskRun.CMD_SCRIPT)

        return resFiles
    }

    protected List<OutputFile> outputFileUrls(TaskRun task, String sas) {
        List<OutputFile> result = new ArrayList<>(20)

        result << destFile(TaskRun.CMD_OUTFILE, task.workDir, sas)
        result << destFile(TaskRun.CMD_ERRFILE, task.workDir, sas)
        result << destFile(TaskRun.CMD_EXIT, task.workDir, sas)

        return result
    }

    protected OutputFile destFile(String localPath, Path targetDir, String sas) {
        log.debug "Task output path: $localPath -> ${targetDir.toUriString()}"
        def target = targetDir.resolve(localPath)
        final dest = new OutputFileBlobContainerDestination()
                .withContainerUrl(AzHelper.toContainerUrl(targetDir,sas))
                .withPath(target.subpath(1,target.nameCount).toString())

        return new OutputFile()
                .withFilePattern(localPath)
                .withDestination( new OutputFileDestination().withContainer(dest) )
                .withUploadOptions( new OutputFileUploadOptions().withUploadCondition( OutputFileUploadCondition.TASK_COMPLETION) )
    }

    protected ImageInformation getImage() {
        List<ImageInformation> images = client.accountOperations().listSupportedImages()

        for (ImageInformation it : images) {
            if( it.osType() != poolOpts.osType )
                continue
            if( it.verificationType() != poolOpts.verification )
                continue
            if( !it.imageReference().publisher().equalsIgnoreCase(poolOpts.publisher) )
                continue
            if( it.imageReference().offer().equalsIgnoreCase(poolOpts.offer) )
                return it
        }

        throw new IllegalStateException("Cannot find a matching VM image with publister=$poolOpts.publisher; offer=$poolOpts.offer; OS type=$poolOpts.osType; verification type=$poolOpts.verification")
    }

    synchronized String getOrCreatePool(TaskRun task) {

        // define a stable pool name
        final loc = config.batch().location
        if( !loc )
            throw new IllegalArgumentException("Missing Azure Batch location")

        final mem = task.config.getMemory()
        final cpus = task.config.getCpus()
        final type = task.config.getMachineType() ?: poolOpts.vmType
        if( !type )
            throw new IllegalArgumentException("Missing Azure Batch VM type")

        final vmType = guessBestVm(loc, cpus, mem, type)
        if( !vmType ) {
            def msg = "Cannot find a VM matching this requirements: type=$type, cpus=${cpus}, mem=${mem?:'-'}, location=${loc}"
            throw new IllegalArgumentException(msg)
        }

        final String poolId = "nf-$vmType.name".toLowerCase()
        final spec = new VmPoolSpec(poolId: poolId, vmType: vmType)
        // add to the list of pool ids
        allPools[poolId] = spec
        
        // check existence and create if needed
        log.debug "[AZURE BATCH] Checking VM pool id=$poolId; size=$vmType"
        if( !client.poolOperations().existsPool(poolId) ) {
            final image = getImage()

            /**
             * A container configuration must be provided for a task to run in a specific container.
             * Such container can be pre-fetched on VM creation or when running the task
             *
             * https://github.com/MicrosoftDocs/azure-docs/blob/master/articles/batch/batch-docker-container-workloads.md#:~:text=Run%20container%20applications%20on%20Azure,compatible%20containers%20on%20the%20nodes.
             */
            final containerConfig = new ContainerConfiguration();

            final vmConfig = new VirtualMachineConfiguration()
                    .withNodeAgentSKUId(image.nodeAgentSKUId())
                    .withImageReference(image.imageReference())
                    .withContainerConfiguration(containerConfig)

            final poolParams = new PoolAddParameter()
                    .withId(poolId)
                    .withVirtualMachineConfiguration(vmConfig)
                    // https://docs.microsoft.com/en-us/azure/batch/batch-pool-vm-sizes
                    .withVmSize(vmType.name)
                    // same as the num ofd cores
                    // https://docs.microsoft.com/en-us/azure/batch/batch-parallel-node-tasks
                    .withTaskSlotsPerNode( vmType.numberOfCores )

            if( poolOpts.autoScale ) {
                poolParams
                    .withEnableAutoScale(true)
                    .withAutoScaleEvaluationInterval( new Period().withSeconds(300) ) // cannot be smaller
                    .withAutoScaleFormula(scalingFormula(poolOpts))
            }
            else {
                poolParams
                        .withTargetDedicatedNodes(poolOpts.vmCount)
            }

            client.poolOperations().createPool(poolParams)
        }

        return poolId
    }

    protected String scalingFormula(AzPoolOpts opts) {
        // https://docs.microsoft.com/en-us/azure/batch/batch-automatic-scaling
        def scalingFormula = '''\
                $TargetDedicatedNodes = {{vmNodes}};
                lifespan         = time() - time("{{now}}");
                span             = TimeInterval_Minute * 60;
                startup          = TimeInterval_Minute * 10;
                ratio            = 50;
                $TargetDedicatedNodes = (lifespan > startup ? (max($RunningTasks.GetSample(span, ratio), $ActiveTasks.GetSample(span, ratio)) == 0 ? 0 : $TargetDedicatedNodes) : {{vmNodes}});
                '''.stripIndent()

        final vars = new HashMap<String,String>()
        vars.vmNodes = opts.vmCount
        vars.now = Instant.now().toString()
        return new MustacheTemplateEngine().render(scalingFormula, vars)
    }

    void deleteTask(AzTaskKey key) {
        client.taskOperations().deleteTask(key.jobId, key.taskId)
    }

    @Override
    void close() {
        // cleanup app successful jobs
        for( Map.Entry<TaskProcessor,String> entry : allJobIds ) {
            final proc = entry.key
            final jobId = entry.value
            if( proc.hasErrors() ) {
                log.debug "Preserving Azure job with error: ${jobId}"
                continue
            }

            try {
                log.trace "Deleting Azure job ${jobId}"
                client.jobOperations().deleteJob(jobId)
            }
            catch (Exception e) {
                log.warn "Unable to delete Azure batch job ${jobId} - Reason: ${e.message ?: e}"
            }
        }
    }
}
