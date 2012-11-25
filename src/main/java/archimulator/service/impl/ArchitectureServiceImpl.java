/*******************************************************************************
 * Copyright (c) 2010-2012 by Min Cai (min.cai.china@gmail.com).
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
package archimulator.service.impl;

import archimulator.model.Architecture;
import archimulator.service.ArchitectureService;
import archimulator.service.ServiceManager;
import archimulator.sim.uncore.cache.replacement.CacheReplacementPolicyType;
import com.j256.ormlite.dao.Dao;
import net.pickapack.model.ModelElement;
import net.pickapack.service.AbstractService;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Min Cai
 */
public class ArchitectureServiceImpl extends AbstractService implements ArchitectureService {
    private Dao<Architecture, Long> architectures;

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public ArchitectureServiceImpl() {
        super(ServiceManager.getDatabaseUrl(), Arrays.<Class<? extends ModelElement>>asList(Architecture.class));

        this.architectures = createDao(Architecture.class);
    }

    @Override
    public void initialize() {
        this.getOrAddArchitecture(true, 2, 2, 32 * 1024, 8, 32 * 1024, 8, 4 * 1024 * 1024, 16, CacheReplacementPolicyType.LRU);
    }

    /**
     *
     * @return
     */
    @Override
    public List<Architecture> getAllArchitectures() {
        return this.getAllItems(this.architectures);
    }

    /**
     *
     * @param first
     * @param count
     * @return
     */
    @Override
    public List<Architecture> getAllArchitectures(long first, long count) {
        return this.getAllItems(this.architectures, first, count);
    }

    /**
     *
     * @return
     */
    @Override
    public long getNumAllArchitectures() {
        return this.getNumAllItems(this.architectures);
    }

    /**
     *
     * @param id
     * @return
     */
    @Override
    public Architecture getArchitectureById(long id) {
        return this.getItemById(this.architectures, id);
    }

    /**
     *
     * @param title
     * @return
     */
    @Override
    public Architecture getArchitectureByTitle(String title) {
        return this.getFirstItemByTitle(this.architectures, title);
    }

    /**
     *
     * @return
     */
    @Override
    public Architecture getFirstArchitecture() {
        return this.getFirstItem(this.architectures);
    }

    /**
     *
     * @param architecture
     */
    @Override
    public void addArchitecture(Architecture architecture) {
        this.addItem(this.architectures, architecture);
    }

    /**
     *
     * @param id
     */
    @Override
    public void removeArchitectureById(long id) {
        this.removeItemById(this.architectures, id);
    }

    /**
     *
     */
    @Override
    public void clearArchitectures() {
        this.clearItems(this.architectures);
    }

    /**
     *
     * @param architecture
     */
    @Override
    public void updateArchitecture(Architecture architecture) {
        this.updateItem(this.architectures, architecture);
    }

    /**
     *
     * @param htLLCRequestProfilingEnabled
     * @param numCores
     * @param numThreadsPerCore
     * @param l1ISize
     * @param l1IAssociativity
     * @param l1DSize
     * @param l1DAssociativity
     * @param l2Size
     * @param l2Associativity
     * @param l2ReplacementPolicyType
     * @return
     */
    @Override
    public Architecture getOrAddArchitecture(boolean htLLCRequestProfilingEnabled, int numCores, int numThreadsPerCore, int l1ISize, int l1IAssociativity, int l1DSize, int l1DAssociativity, int l2Size, int l2Associativity, CacheReplacementPolicyType l2ReplacementPolicyType) {
        Architecture architecture = new Architecture(htLLCRequestProfilingEnabled, numCores, numThreadsPerCore, l1ISize, l1IAssociativity, l1DSize, l1DAssociativity, l2Size, l2Associativity, l2ReplacementPolicyType);

        Architecture architectureWithSameTitle = getArchitectureByTitle(architecture.getTitle());
        if(architectureWithSameTitle == null) {
            addArchitecture(architecture);
            return architecture;
        }

        return architectureWithSameTitle;
    }
}
