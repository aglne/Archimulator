package archimulator.sim.base.event;

import archimulator.sim.base.simulation.Simulation;

import java.util.Map;

public class PollStatsEvent extends SimulationEvent {
    private Map<String, Object> stats;

    public PollStatsEvent(Simulation simulation, Map<String, Object> stats) {
        super(simulation);
        this.stats = stats;
    }

    public Map<String, Object> getStats() {
        return stats;
    }
}
