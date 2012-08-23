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
package archimulator.client;

import archimulator.model.ExperimentPack;
import archimulator.service.ServiceManager;
import net.pickapack.JsonSerializationHelper;
import org.parboiled.common.FileUtils;

import java.io.File;

public class ExperimentStatsCollectorStartup {
    public static void main(String[] args) {
        if (args.length == 1) {
            String arg = args[0];
            if (new File(arg).exists()) {
                ExperimentPackSpec experimentPackSpec = JsonSerializationHelper.deserialize(ExperimentPackSpec.class, FileUtils.readAllText(arg));
                arg = experimentPackSpec.getTitle();
            }

            ExperimentPack experimentPack = ServiceManager.getExperimentService().getExperimentPackByTitle(arg);
            if (experimentPack != null) {
                experimentPack.dump(false);
            } else {
                System.err.println("Experiment pack \"" + arg + "\" do not exist");
            }
        } else {
            for (ExperimentPack experimentPack : ServiceManager.getExperimentService().getAllExperimentPacks()) {
                experimentPack.dump(false);
                System.out.println();
            }
        }
    }
}
