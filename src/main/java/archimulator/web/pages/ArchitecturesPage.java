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
package archimulator.web.pages;

import archimulator.model.Architecture;
import archimulator.service.ServiceManager;
import archimulator.web.components.PagingNavigator;
import net.pickapack.StorageUnit;
import net.pickapack.dateTime.DateHelper;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.wicketstuff.annotation.mount.MountPath;

import java.util.Iterator;

@MountPath(value = "/", alt = "/architectures")
public class ArchitecturesPage extends AuthenticatedWebPage {
    public ArchitecturesPage(PageParameters parameters) {
        super(PageType.ARCHITECTURES, parameters);

        setTitle("Architectures - Archimulator");

        IDataProvider<Architecture> dataProvider = new IDataProvider<Architecture>() {
            @Override
            public Iterator<? extends Architecture> iterator(long first, long count) {
                return ServiceManager.getArchitectureService().getAllArchitectures(first, count).iterator();
            }

            @Override
            public long size() {
                return ServiceManager.getArchitectureService().getNumAllArchitectures();
            }

            @Override
            public IModel<Architecture> model(Architecture object) {
                return new Model<Architecture>(object);
            }

            @Override
            public void detach() {
            }
        };

        DataView<Architecture> rowArchitecture = new DataView<Architecture>("row_architecture", dataProvider) {
            @Override
            protected void populateItem(Item<Architecture> item) {
                final Architecture architecture = item.getModelObject();

                item.add(new Label("cell_id", architecture.getId() + ""));
                item.add(new Label("cell_title", architecture.getTitle()));
                item.add(new Label("cell_num_cores", architecture.getNumCores() + ""));
                item.add(new Label("cell_num_threads_per_core", architecture.getNumThreadsPerCore() + ""));
                item.add(new Label("cell_l1I_size", StorageUnit.toString(architecture.getL1ISize())));
                item.add(new Label("cell_l1I_associativity", architecture.getL1IAssociativity() + ""));
                item.add(new Label("cell_l1D_size", StorageUnit.toString(architecture.getL1DSize())));
                item.add(new Label("cell_l1D_associativity", architecture.getL1DAssociativity() + ""));
                item.add(new Label("cell_l2_size", StorageUnit.toString(architecture.getL2Size())));
                item.add(new Label("cell_l2_associativity", architecture.getL2Associativity() + ""));
                item.add(new Label("cell_l2_repl", architecture.getL2ReplacementPolicyType() + ""));
                item.add(new Label("cell_create_time", DateHelper.toString(architecture.getCreateTime())));

                item.add(new WebMarkupContainer("cell_operations") {{
                    add(new Link<Void>("button_edit") {
                        @Override
                        public void onClick() {
                            PageParameters pageParameters1 = new PageParameters();
                            pageParameters1.set("action", "edit");
                            pageParameters1.set("architecture_id", architecture.getId());
                            pageParameters1.set("back_page_id", getPageId());

                            setResponsePage(ArchitecturePage.class, pageParameters1);
                        }
                    });
                }});
            }
        };
        rowArchitecture.setItemsPerPage(10);
        add(rowArchitecture);

        add(new PagingNavigator("navigator", rowArchitecture));
    }
}
