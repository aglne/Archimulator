package archimulator.sim.uncore.coherence.msi.event.dir;

import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.msi.controller.CacheController;
import archimulator.sim.uncore.coherence.msi.controller.DirectoryController;
import archimulator.sim.uncore.coherence.msi.flow.CacheCoherenceFlow;

public class PutSLastEvent extends DirectoryControllerEvent {
    private CacheController req;

    public PutSLastEvent(DirectoryController generator, CacheCoherenceFlow producerFlow, CacheController req, int tag, MemoryHierarchyAccess access) {
        super(generator, producerFlow, DirectoryControllerEventType.PUTS_LAST, access, tag);
        this.req = req;
    }

    public CacheController getReq() {
        return req;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s: PutSLastEvent{id=%d, req=%s, tag=0x%08x}", getBeginCycle(), getGenerator(), getId(), req, getTag());
    }
}
