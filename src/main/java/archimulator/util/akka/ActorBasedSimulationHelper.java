package archimulator.util.akka;

import akka.actor.*;
import org.apache.commons.lang.time.StopWatch;
import org.jfree.data.statistics.Statistics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ActorBasedSimulationHelper {
    public static void main(String[] args) {
        execute();
    }

    private static long execute() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        ActorSystem system = ActorSystem.create("experiment");

        ActorRef processor = system.actorOf(new Props(new UntypedActorFactory() {
            public UntypedActor create() {
                return new Processor(4, 4);
            }
        }), "processor");

        processor.tell(new SimulationConfig("mst", 10000));

        system.awaitTermination();

        stopWatch.stop();
        System.out.println(stopWatch.getTime());

        return stopWatch.getTime();
    }

    private static class SimulationConfig implements Serializable {
        private String workload;
        private int maxCycles;

        public SimulationConfig(String workload, int maxCycles) {
            this.workload = workload;
            this.maxCycles = maxCycles;
        }
    }

    private static class ExecuteOneCycle implements Serializable {
        private int currentCycle;

        public ExecuteOneCycle(int currentCycle) {
            this.currentCycle = currentCycle;
        }
    }

    private static class OneCycleExecuted implements Serializable {
    }

    public static class Core extends UntypedActor {
        @Override
        public void onReceive(Object o) throws Exception {
            if (o instanceof ExecuteOneCycle) {
                ExecuteOneCycle executeOneCycle = (ExecuteOneCycle) o;
                System.out.printf("[%s: %d] Execute one cycle.\n", getSelf().path().name(), executeOneCycle.currentCycle);
                getSender().tell(new OneCycleExecuted(), getSelf());
            } else {
                unhandled(o);
            }
        }
    }

    private static class Processor extends UntypedActor {
        private int numCores;
        private int numThreadsPerCore;

        private List<ActorRef> cores;

        private int currentCycle = 0;
        private int maxCycles = 0;

        private int numPendings = 0;

        public Processor(int numCores, int numThreadsPerCore) {
            this.numCores = numCores;
            this.numThreadsPerCore = numThreadsPerCore;

            this.cores = new ArrayList<ActorRef>();

            for (int i = 0; i < numCores; i++) {
                for (int j = 0; j < numThreadsPerCore; j++) {
                    this.cores.add(
                            this.getContext().actorOf(new Props(new UntypedActorFactory() {
                                public UntypedActor create() {
                                    return new Core();
                                }
                            }), "core_" + i + "_" + j));
                }
            }
        }

        @Override
        public void onReceive(Object o) throws Exception {
            if (o instanceof SimulationConfig) {
                SimulationConfig simulationConfig = (SimulationConfig) o;
                maxCycles = simulationConfig.maxCycles;

                executeOneCycle(currentCycle);
            } else if (o instanceof OneCycleExecuted) {
                OneCycleExecuted oneCycleExecuted = (OneCycleExecuted) o;

                numPendings--;

                if (numPendings == 0) {
                    System.out.printf("[%s: %d] Execute one cycle.\n", getSelf().path().name(), currentCycle);

                    currentCycle++;

                    if (currentCycle < maxCycles) {
                        executeOneCycle(currentCycle);
                    } else {
                        System.out.println("Simulation stopped.");
                        getContext().system().shutdown();
                    }
                }
            } else {
                unhandled(o);
            }
        }

        private void executeOneCycle(int currentCycle) {
            numPendings = this.cores.size();

            for (ActorRef core : cores) {
                core.tell(new ExecuteOneCycle(currentCycle), getSelf());
            }
        }
    }
}