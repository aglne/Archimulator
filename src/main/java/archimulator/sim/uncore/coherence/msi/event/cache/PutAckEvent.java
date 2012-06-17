package archimulator.sim.uncore.coherence.msi.event.cache;

import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.msi.controller.CacheController;
import archimulator.sim.uncore.coherence.msi.flow.CacheCoherenceFlow;

public class PutAckEvent extends CacheControllerEvent {
    public PutAckEvent(CacheController generator, CacheCoherenceFlow producerFlow, int tag, MemoryHierarchyAccess access) {
        super(generator, producerFlow, CacheControllerEventType.PUT_ACK, access, tag);
    }

    @Override
    public String toString() {
        return String.format("[%d] %s: PutAckEvent{id=%d, tag=0x%08x}", getBeginCycle(), getGenerator(), getId(), getTag());
    }
}
