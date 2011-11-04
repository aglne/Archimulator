/*******************************************************************************
 * Copyright (c) 2010-2011 by Min Cai (min.cai.china@gmail.com).
 *
 * This file is part of the Archimulator multicore architectural simulator.
 *
 * Archimulator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Archimulator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Archimulator. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package archimulator.mem.cache.eviction.ext;

import archimulator.core.BasicThread;
import archimulator.core.Processor;
import archimulator.mem.cache.CacheHit;
import archimulator.mem.cache.CacheLine;
import archimulator.mem.cache.CacheMiss;
import archimulator.mem.cache.EvictableCache;
import archimulator.mem.cache.eviction.EvictionPolicy;
import archimulator.mem.cache.eviction.EvictionPolicyFactory;
import archimulator.mem.cache.eviction.LeastRecentlyUsedEvictionPolicy;
import archimulator.mem.ext.delinquentLoad.DelinquentLoadIdentificationCapability;
import archimulator.util.action.Action1;
import archimulator.sim.event.ProcessorInitializedEvent;

import java.io.Serializable;

public class TestEvictionPolicy1<StateT extends Serializable, LineT extends CacheLine<StateT>> extends LeastRecentlyUsedEvictionPolicy<StateT, LineT> {
    private Processor processor;

    public TestEvictionPolicy1(EvictableCache<StateT, LineT> cache) {
        super(cache);

        cache.getBlockingEventDispatcher().addListener(ProcessorInitializedEvent.class, new Action1<ProcessorInitializedEvent>() {
            public void apply(ProcessorInitializedEvent event) {
                TestEvictionPolicy1.this.processor = event.getProcessor();
            }
        });
    }

//    @Override
//    public CacheMiss<StateT, LineT> handleReplacement(CacheReference reference) {
//        if (BasicThread.isHelperThread(reference.getThreadId()) && !isDelinquentLoad(reference.getPc())) {
//            return new CacheMiss<>(reference, -1, true);
//        } else {
//            return super.handleReplacement(reference);
//        }
//    }

    private boolean isDelinquentPc(int pc) {
        return this.processor.getCapability(DelinquentLoadIdentificationCapability.class).isDelinquentPc(BasicThread.getMainThreadId(), pc);
    }

    @Override
    public void handlePromotionOnHit(CacheHit<StateT, LineT> hit) {
        if (this.isDelinquentPc(hit.getReference().getAccess().getVirtualPc())) {
            this.setMRU(hit.getReference().getSet(), hit.getWay());
        } else {
            this.setStackPosition(hit.getReference().getSet(), hit.getWay(), Math.max(this.getStackPosition(hit.getReference().getSet(), hit.getWay()) - 1, 0));
        }
    }

    @Override
    public void handleInsertionOnMiss(CacheMiss<StateT, LineT> miss) {
        if (this.isDelinquentPc(miss.getReference().getAccess().getVirtualPc())) {
            this.setMRU(miss.getReference().getSet(), miss.getWay());
        } else {
//            this.setLRU(miss.getReference().getSet(), miss.getWay());
            this.setStackPosition(miss.getReference().getSet(), miss.getWay(), 4);
        }
    }

    public static final EvictionPolicyFactory FACTORY = new EvictionPolicyFactory() {
        public String getName() {
            return "TEST_1";
        }

        public <StateT extends Serializable, LineT extends CacheLine<StateT>> EvictionPolicy<StateT, LineT> create(EvictableCache<StateT, LineT> cache) {
            return new TestEvictionPolicy1<StateT, LineT>(cache);
        }
    };
}
