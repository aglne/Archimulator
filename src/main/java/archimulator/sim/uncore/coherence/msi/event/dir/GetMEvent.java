package archimulator.sim.uncore.coherence.msi.event.dir;

import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.msi.controller.CacheController;
import archimulator.sim.uncore.coherence.msi.controller.DirectoryController;
import archimulator.sim.uncore.coherence.msi.flow.CacheCoherenceFlow;
import net.pickapack.action.Action;

public class GetMEvent extends DirectoryControllerEvent {
    private CacheController req;
    private int set;
    private int way;
    private Action onStalledCallback;

    public GetMEvent(DirectoryController generator, CacheCoherenceFlow producerFlow, CacheController req, int tag, int set, int way, Action onStalledCallback, MemoryHierarchyAccess access) {
        super(generator, producerFlow, DirectoryControllerEventType.GETM, access, tag);
        this.req = req;
        this.set = set;
        this.way = way;
        this.onStalledCallback = onStalledCallback;
    }

    public CacheController getReq() {
        return req;
    }

    public int getSet() {
        return set;
    }

    public int getWay() {
        return way;
    }

    public Action getOnStalledCallback() {
        return onStalledCallback;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s: GetMEvent{id=%d, req=%s, tag=0x%08x, set=%d, way=%d}", getBeginCycle(), getGenerator(), getId(), req, getTag(), set, way);
    }
}
