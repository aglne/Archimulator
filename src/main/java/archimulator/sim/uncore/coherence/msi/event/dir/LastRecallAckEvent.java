package archimulator.sim.uncore.coherence.msi.event.dir;

import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.msi.controller.DirectoryController;
import archimulator.sim.uncore.coherence.msi.flow.CacheCoherenceFlow;

public class LastRecallAckEvent extends DirectoryControllerEvent {
    public LastRecallAckEvent(DirectoryController generator, CacheCoherenceFlow producerFlow, int tag, MemoryHierarchyAccess access) {
        super(generator, producerFlow, DirectoryControllerEventType.LAST_RECALL_ACK, access, tag);
    }

    @Override
    public String toString() {
        return String.format("[%d] %s: LastRecallAckEvent{id=%d, tag=0x%08x}", getBeginCycle(), getGenerator(), getId(), getTag());
    }
}
