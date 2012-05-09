package archimulator.sim.uncore.coherence.flow.llc;

import archimulator.sim.uncore.CacheAccessType;
import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.common.MESIState;
import archimulator.sim.uncore.coherence.flc.FirstLevelCache;
import archimulator.sim.uncore.coherence.flow.FindAndLockFlow;
import archimulator.sim.uncore.coherence.flow.Flow;
import archimulator.sim.uncore.coherence.flow.LockingFlow;
import archimulator.sim.uncore.coherence.flow.flc.L2UpwardWriteFlow;
import archimulator.sim.uncore.coherence.llc.LastLevelCache;
import archimulator.util.Reference;
import archimulator.util.action.Action;

public class L1DownwardWriteFlow extends LockingFlow {
    private LastLevelCache cache;
    private FirstLevelCache source;
    private MemoryHierarchyAccess access;
    private int tag;

    public L1DownwardWriteFlow(Flow producerFlow, final LastLevelCache cache, final FirstLevelCache source, MemoryHierarchyAccess access, int tag) {
        super(producerFlow);
        this.cache = cache;
        this.source = source;
        this.access = access;
        this.tag = tag;
    }

    public void run(final Action onSuccessCallback, final Action onFailureCallback) {
        this.onCreate(this.cache.getCycleAccurateEventQueue().getCurrentCycle());

        final FindAndLockFlow findAndLockFlow = new LastLevelCacheFindAndLockFlow(this, this.cache, this.access, this.tag, CacheAccessType.DOWNWARD_READ);

        findAndLockFlow.start(
                new Action() {
                    @Override
                    public void apply() {
                        invalidateSharers(findAndLockFlow, onSuccessCallback);
                    }
                }, new Action() {
                    @Override
                    public void apply() {
//                        findAndLockFlow.getCacheAccess().abort();
//                        findAndLockFlow.getCacheAccess().getLine().unlock();
//
                        getCache().sendReply(source, 8, onFailureCallback);
                        onDestroy();
                    }
                }, new Action() {
                    @Override
                    public void apply() {
                        findAndLockFlow.getCacheAccess().abort();
                        findAndLockFlow.getCacheAccess().getLine().unlock();

                        getCache().sendReply(source, 8, onFailureCallback);
                        onDestroy();
                    }
                }
        );
    }

    private void invalidateSharers(final FindAndLockFlow findAndLockFlow, final Action onSuccessCallback) {
        final Reference<Integer> pending = new Reference<Integer>(0);

        for (final FirstLevelCache sharer : getCache().getSharers(tag)) {
            if (sharer != source) {
                getCache().sendRequest(sharer, 8, new Action() {
                    @Override
                    public void apply() {
                        L2UpwardWriteFlow l2UpwardWriteFlow = new L2UpwardWriteFlow(L1DownwardWriteFlow.this, sharer, getCache(), access, tag);
                        l2UpwardWriteFlow.start(
                                new Action() {
                                    @Override
                                    public void apply() {
                                        pending.set(pending.get() - 1);

                                        if (pending.get() == 0) {
                                            getData(findAndLockFlow, onSuccessCallback);
                                        }
                                    }
                                }
                        );
                    }
                });
                pending.set(pending.get() + 1);
            }
        }

        if (pending.get() == 0) {
            getData(findAndLockFlow, onSuccessCallback);
        }
    }

    private void getData(final FindAndLockFlow findAndLockFlow, final Action onSuccessCallback) {
        if (!findAndLockFlow.getCacheAccess().isHitInCache() && !getCache().isOwnedOrShared(tag)) {
            getCache().sendRequest(getCache().getNext(), 8, new Action() {
                @Override
                public void apply() {
                    getCache().getNext().memReadRequestReceive(getCache(), tag, new Action() {
                        @Override
                        public void apply() {
                            endGetData(findAndLockFlow, onSuccessCallback);
                        }
                    });
                }
            });
        }
        else {
            endGetData(findAndLockFlow, onSuccessCallback);
        }
    }

    private void endGetData(FindAndLockFlow findAndLockFlow, Action onSuccessCallback) {
        for (final FirstLevelCache sharer : getCache().getSharers(tag)) {
            getCache().getShadowTagDirectories().get(sharer).removeTag(tag);
        }

        getCache().getShadowTagDirectories().get(source).addTag(tag);

        if (findAndLockFlow.getCacheAccess().isHitInCache() || !findAndLockFlow.getCacheAccess().isBypass()) {
            findAndLockFlow.getCacheAccess().getLine().setNonInitialState(findAndLockFlow.getCacheAccess().getLine().getState() == MESIState.MODIFIED ? MESIState.MODIFIED : MESIState.EXCLUSIVE);
        }

        findAndLockFlow.getCacheAccess().commit().getLine().unlock();

        getCache().sendReply(source, source.getCache().getLineSize() + 8, onSuccessCallback);
        onDestroy();
    }

    public LastLevelCache getCache() {
        return cache;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: L1DownwardWriteFlow#%d", getBeginCycle(), getCache().getName(), getId());
    }
}
