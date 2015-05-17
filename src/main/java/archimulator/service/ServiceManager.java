/*******************************************************************************
 * Copyright (c) 2010-2014 by Min Cai (min.cai.china@gmail.com).
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
package archimulator.service;

import archimulator.service.impl.BenchmarkServiceImpl;
import archimulator.util.PropertiesHelper;

/**
 * Helper class for retrieving services.
 *
 * @author Min Cai
 */
public class ServiceManager {
    /**
     * User home template argument. To be used in benchmark arguments injection.
     */
    public static final String USER_HOME_TEMPLATE_ARG = "%user.home%";

    private static BenchmarkService benchmarkService;

    /**
     * Static constructor.
     */
    static {
        System.out.println("Archimulator (version: " + PropertiesHelper.getVersion() + ") - CMP Architectural Simulator Written in Java.");
        System.out.println("Copyright (c) 2010-2015 by Min Cai (min.cai.china@gmail.com).\n");

        benchmarkService = new BenchmarkServiceImpl();
    }

    /**
     * Get the benchmark service singleton.
     *
     * @return benchmark service singleton
     */
    public static BenchmarkService getBenchmarkService() {
        return benchmarkService;
    }
}
