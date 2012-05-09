package archimulator.sim.uncore.coherence.flow.flc;

import archimulator.sim.uncore.CacheAccessType;
import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.common.MESIState;
import archimulator.sim.uncore.coherence.flc.FirstLevelCache;
import archimulator.sim.uncore.coherence.flow.FindAndLockFlow;
import archimulator.sim.uncore.coherence.flow.LockingFlow;
import archimulator.sim.uncore.coherence.flow.llc.L1DownwardWriteFlow;
import archimulator.util.action.Action;

public class StoreFlow extends LockingFlow {
    private FirstLevelCache cache;
    private MemoryHierarchyAccess access;
    private int tag;

    public StoreFlow(FirstLevelCache cache, MemoryHierarchyAccess access, int tag) {
        this.cache = cache;
        this.access = access;
        this.tag = tag;
    }

    public void run(final Action onSuccessCallback, final Action onFailureCallback) {
        pendings++;

        final FindAndLockFlow findAndLockFlow = new FirstLevelCacheFindAndLockFlow(this.cache, this.access, this.tag, CacheAccessType.STORE);

        findAndLockFlow.start(
                new Action() {
                    @Override
                    public void apply() {
                        //TODO: error?
                        if (findAndLockFlow.getCacheAccess().getLine().getState() == MESIState.SHARED || findAndLockFlow.getCacheAccess().getLine().getState() == MESIState.INVALID) {
                            downwardWrite(findAndLockFlow, onSuccessCallback, onFailureCallback);
                        } else {
                            findAndLockFlow.getCacheAccess().getLine().setNonInitialState(MESIState.MODIFIED);
                            findAndLockFlow.getCacheAccess().commit().getLine().unlock();

                            endFillOrEvict(findAndLockFlow);

                            afterFlowEnd(findAndLockFlow);

                            onSuccessCallback.apply();

                            pendings--;
                        }
                    }
                }, new Action() {
                    @Override
                    public void apply() {
//                        findAndLockFlow.getCacheAccess().abort();
//                        findAndLockFlow.getCacheAccess().getLine().unlock();
//
//                        afterFlowEnd(findAndLockFlow);

                        onFailureCallback.apply();

                        pendings--;
                    }
                }, new Action() {
                    @Override
                    public void apply() {
                        findAndLockFlow.getCacheAccess().abort();
                        findAndLockFlow.getCacheAccess().getLine().unlock();

                        afterFlowEnd(findAndLockFlow);

                        onFailureCallback.apply();

                        pendings--;
                    }
                }
        );
    }

    private void downwardWrite(final FindAndLockFlow findAndLockFlow, final Action onSuccessCallback, final Action onFailureCallback) {
        getCache().sendRequest(getCache().getNext(), 8, new Action() {
            @Override
            public void apply() {
                L1DownwardWriteFlow l1DownwardWriteFlow = new L1DownwardWriteFlow(getCache().getNext(), getCache(), access, tag);
                l1DownwardWriteFlow.run(
                        new Action() {
                            @Override
                            public void apply() {
                                findAndLockFlow.getCacheAccess().getLine().setNonInitialState(MESIState.MODIFIED);
                                findAndLockFlow.getCacheAccess().commit().getLine().unlock();

                                endFillOrEvict(findAndLockFlow);

                                afterFlowEnd(findAndLockFlow);

                                onSuccessCallback.apply();

                                pendings--;
                            }
                        }, new Action() {
                            @Override
                            public void apply() {
                                getCache().getCycleAccurateEventQueue().schedule(new Action() {
                                    public void apply() {
                                        downwardWrite(findAndLockFlow, onSuccessCallback, onFailureCallback);
                                    }
                                }, getCache().getRetryLatency());

//                                            throw new UnsupportedOperationException(); //TODO
                            }
                        }
                );
            }
        });
    }

    public FirstLevelCache getCache() {
        return cache;
    }

    private static int pendings = 0;
}
