/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.utils.TimeProfiler;
import org.sonar.api.utils.ValidationMessages;
import org.sonar.core.technicaldebt.TechnicalDebtModelSynchronizer;
import org.sonar.core.technicaldebt.TechnicalDebtRuleCache;
import org.sonar.server.rule.RuleRegistration;

public final class RegisterTechnicalDebtModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegisterTechnicalDebtModel.class);

  private final TechnicalDebtModelSynchronizer manager;
  private final RuleFinder ruleFinder;

  /**
   * @param registerRulesBeforeModels used only to be started after the creation of check templates
   */
  public RegisterTechnicalDebtModel(TechnicalDebtModelSynchronizer manager, RuleFinder ruleFinder, RuleRegistration registerRulesBeforeModels) {
    this.manager = manager;
    this.ruleFinder = ruleFinder;
  }

  public void start() {
    TimeProfiler profiler = new TimeProfiler(LOGGER).start("Register Technical Debt Model");
    TechnicalDebtRuleCache technicalDebtRuleCache = new TechnicalDebtRuleCache(ruleFinder);
    manager.synchronize(ValidationMessages.create(), technicalDebtRuleCache);
    profiler.stop();
  }

}
