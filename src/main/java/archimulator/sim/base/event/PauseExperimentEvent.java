package archimulator.sim.base.event;

import archimulator.sim.base.experiment.Experiment;

public class PauseExperimentEvent extends ExperimentEvent {
    public PauseExperimentEvent(Experiment experiment) {
        super(experiment);
    }
}
