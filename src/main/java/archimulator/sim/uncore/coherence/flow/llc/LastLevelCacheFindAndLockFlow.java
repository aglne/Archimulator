package archimulator.sim.uncore.coherence.flow.llc;

import archimulator.sim.uncore.CacheAccessType;
import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.common.MESIState;
import archimulator.sim.uncore.coherence.event.LastLevelCacheLineEvictedByMemWriteProcessEvent;
import archimulator.sim.uncore.coherence.flow.FindAndLockFlow;
import archimulator.sim.uncore.coherence.llc.LastLevelCache;
import archimulator.util.action.Action;

public class LastLevelCacheFindAndLockFlow extends FindAndLockFlow {
    public LastLevelCacheFindAndLockFlow(LastLevelCache cache, MemoryHierarchyAccess access, int tag, CacheAccessType cacheAccessType) {
        super(cache, access, tag, cacheAccessType);
    }

    @Override
    protected void evict(MemoryHierarchyAccess access, final Action onSuccessCallback, Action onFailureCallback) {
        if (getCacheAccess().getLine().getState() == MESIState.MODIFIED) {
            getCache().sendRequest(getCache().getNext(), getCache().getCache().getLineSize() + 8, new Action() {
                @Override
                public void apply() {
                    getCache().getNext().memWriteRequestReceive(getCache(), getCacheAccess().getLine().getTag(), new Action() {
                        @Override
                        public void apply() {
                            getCache().getBlockingEventDispatcher().dispatch(new LastLevelCacheLineEvictedByMemWriteProcessEvent(getCache(), getCacheAccess().getLine()));
                            onSuccessCallback.apply();
                        }
                    });
                }
            });
        } else {
            getCache().getCycleAccurateEventQueue().schedule(this, new Action() {
                @Override
                public void apply() {
                    getCache().getBlockingEventDispatcher().dispatch(new LastLevelCacheLineEvictedByMemWriteProcessEvent(getCache(), getCacheAccess().getLine()));
                    onSuccessCallback.apply();
                }
            }, 0);
        }
    }

    @Override
    public LastLevelCache getCache() {
        return (LastLevelCache) super.getCache();
    }
}
