/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.studio.service.builder;

import com.axelor.exception.AxelorException;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.repo.MetaActionRepository;
import com.axelor.meta.loader.XMLViews;
import com.axelor.meta.schema.ObjectViews;
import com.axelor.meta.schema.actions.Action;
import com.axelor.studio.db.ActionBuilder;
import com.axelor.studio.db.repo.ActionBuilderRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionBuilderService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private ActionViewBuilderService actionViewBuilderService;

  @Inject private ActionScriptBuilderService actionScriptBuilderService;

  @Inject private ActionEmailBuilderService actionEmailBuilderService;

  @Inject private MetaActionRepository metaActionRepo;

  @Transactional
  public MetaAction build(ActionBuilder builder) {

    if (builder == null) {
      return null;
    }

    Integer typeSelect = builder.getTypeSelect();
    log.debug("Processing action: {}, type: {}", builder.getName(), builder.getTypeSelect());

    if (typeSelect < ActionBuilderRepository.TYPE_SCRIPT
        && (builder.getLines() == null || builder.getLines().isEmpty())) {
      return null;
    }

    MetaAction metaAction = null;

    if (typeSelect <= ActionBuilderRepository.TYPE_SCRIPT) {
      metaAction = actionScriptBuilderService.build(builder);
    } else if (typeSelect == ActionBuilderRepository.TYPE_VIEW) {
      metaAction = actionViewBuilderService.build(builder);
    } else if (typeSelect == ActionBuilderRepository.TYPE_EMAIL) {
      metaAction = actionEmailBuilderService.build(builder);
    }

    if (builder.getMetaModule() != null) {
      metaAction.setModule(builder.getMetaModule().getName());
    }

    MetaStore.clear();

    return metaAction;
  }

  public List<Action> build(List<MetaJsonField> fields, String module)
      throws AxelorException, JAXBException {

    List<Action> actions = new ArrayList<>();
    if (fields == null || module == null) {
      return actions;
    }

    for (MetaJsonField field : fields) {
      addActions(module, field.getOnChange(), actions);
      addActions(module, field.getOnClick(), actions);
    }

    return actions;
  }

  private void addActions(String module, String actionNames, List<Action> actions)
      throws JAXBException {

    if (actionNames == null) {
      return;
    }

    String[] names = actionNames.split(",");

    for (String name : names) {
      if (name.equals("save")) {
        continue;
      }
      MetaAction metaAction =
          metaActionRepo.all().filter("self.name = ?1 and self.module is null", name).fetchOne();
      if (metaAction != null) {
        ObjectViews views = XMLViews.fromXML(metaAction.getXml());
        Action action = views.getActions().get(0);
        action.setXmlId(module + "-" + action.getName());
        actions.add(action);
      }
    }
  }
}
