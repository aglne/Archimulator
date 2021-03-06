package archimulator.util.cmd;

import archimulator.common.*;
import archimulator.uncore.cache.replacement.CacheReplacementPolicyType;
import archimulator.uncore.dram.MemoryControllerType;
import archimulator.uncore.net.simple.routing.RoutingAlgorithmType;
import archimulator.util.serialization.JsonSerializationHelper;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulate command.
 *
 * @author Min Cai
 */
@Parameters(commandNames = "simulate", separators = "=")
public class SimulateCommand {
    @Parameter(names = "-t", description = "Experiment type")
    private ExperimentType experimentType = ExperimentType.TWO_PHASE;

    @Parameter(required = true, names = "-d", description = "Output directory for all generated files")
    private String outputDirectory = null;

    @Parameter(required = true, names = "-e", description = "Executable to be simulated")
    private String executable = null;

    @Parameter(names = "-a", description = "Arguments passed to the executable")
    private String arguments = "";

    @Parameter(names = "-numMaxInsts", description = "Number of maximum instructions executed")
    private long numMaxInstructions = -1;

    @Parameter(names = {"-n", "-numCores"}, description = "Number of cores")
    private int numCores = 2;

    @Parameter(names = "-numThreadsPerCore", description = "Number of threads per core")
    private int numThreadsPerCore = 2;

    @Parameter(names = "-l1ISize", description = "L1 instruction cache size(B,KB,MB,GB)")
    private String l1ISize = "64KB";

    @Parameter(names = "-l1IAssoc", description = "L1 instruction cache associativity")
    private int l1IAssociativity = 4;

    @Parameter(names = "-l1DSize", description = "L1 data cache size(B,KB,MB,GB)")
    private String l1DSize = "64KB";

    @Parameter(names = "-l1DAssoc", description = "L1 data cache associativity")
    private int l1DAssociativity = 4;

    @Parameter(names = "-l2Size", description = "L2 cache size(B,KB,MB,GB)")
    private String l2Size = "512KB";

    @Parameter(names = "-l2Assoc", description = "L2 cache associativity")
    private int l2Associativity = 16;

    @Parameter(names = "-l2Repl", description = "L2 cache replacement policy type")
    private CacheReplacementPolicyType l2ReplacementPolicyType = CacheReplacementPolicyType.LRU;

    @Parameter(names = "-routing", description = "On-chip interconnect routing algorithm type")
    private RoutingAlgorithmType routingAlgorithmType = RoutingAlgorithmType.BASIC_STATIC;

    @Parameter(names = "-mcType", description = "Memory controller type")
    private MemoryControllerType memoryControllerType = MemoryControllerType.FIXED_LATENCY;

    /**
     * Run the simulate command.
     */
    public void run() {
        List<ContextMapping> contextMappings = new ArrayList<>();
        contextMappings.add(new ContextMapping(0, executable, arguments));

        Experiment experiment = new Experiment(experimentType, outputDirectory, false, -1, numCores, numThreadsPerCore, (int) displaySizeToByteCount(l1ISize), l1IAssociativity, (int) displaySizeToByteCount(l1DSize), l1DAssociativity, (int) displaySizeToByteCount(l2Size), l2Associativity, l2ReplacementPolicyType, routingAlgorithmType, memoryControllerType, numMaxInstructions, contextMappings);
        experiment.run();

        if (experiment.getState() == ExperimentState.COMPLETED) {
            File file = new File(outputDirectory, "result.json");

            if (!file.exists()) {
                List<ExperimentStat> stats = experiment.getStats();

                String json = JsonSerializationHelper.toJson(stats, true);

                if (!file.getParentFile().exists()) {
                    if (!file.getParentFile().mkdirs()) {
                        throw new RuntimeException();
                    }
                }

                try {
                    FileUtils.writeStringToFile(file, json);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                System.out.println("Experiment statistics has been written to " + file.getPath());
            }
        }
    }

    /**
     * Convert from display size to byte count.
     *
     * @param displaySize the display size
     * @return the byte count
     */
    private long displaySizeToByteCount(String displaySize) {
        displaySize = displaySize.trim();
        displaySize = displaySize.replaceAll(",", ".");
        try {
            return Long.parseLong(displaySize);
        } catch (NumberFormatException ignored) {
        }
        final Matcher m = Pattern.compile("([\\d.,]+)\\s*(\\w)").matcher(displaySize);
        m.find();
        int scale = 1;
        switch (m.group(2).charAt(0)) {
            case 'G':
                scale *= 1024;
            case 'M':
                scale *= 1024;
            case 'K':
                scale *= 1024;
                break;
            default:
                throw new IllegalArgumentException();
        }
        return Math.round(Double.parseDouble(m.group(1)) * scale);
    }
}
