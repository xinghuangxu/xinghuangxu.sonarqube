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
package org.sonar.batch.index;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.sonar.api.database.model.MeasureModel;
import org.sonar.api.database.model.Snapshot;
import org.sonar.api.measures.*;
import org.sonar.api.resources.JavaFile;
import org.sonar.api.resources.JavaPackage;
import org.sonar.api.resources.Project;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.utils.SonarException;
import org.sonar.core.persistence.AbstractDaoTestCase;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class MeasurePersisterTest extends AbstractDaoTestCase {

  @org.junit.Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final String SHORT = "SHORT";
  private static final String LONG = StringUtils.repeat("0123456789", 10);
  private static final String TOO_LONG = StringUtils.repeat("0123456789", 401);

  public static final int PROJECT_SNAPSHOT_ID = 3001;
  public static final int PACKAGE_SNAPSHOT_ID = 3002;
  public static final int FILE_SNAPSHOT_ID = 3003;
  public static final int COVERAGE_METRIC_ID = 2;

  MeasurePersister measurePersister;
  RuleFinder ruleFinder = mock(RuleFinder.class);
  ResourcePersister resourcePersister = mock(ResourcePersister.class);
  MemoryOptimizer memoryOptimizer = mock(MemoryOptimizer.class);
  Project project = new Project("foo");
  JavaPackage aPackage = new JavaPackage("org.foo");
  JavaFile aFile = new JavaFile("org.foo.Bar");
  Snapshot projectSnapshot = snapshot(PROJECT_SNAPSHOT_ID);
  Snapshot packageSnapshot = snapshot(PACKAGE_SNAPSHOT_ID);

  @Before
  public void mockResourcePersister() {
    when(resourcePersister.getSnapshotOrFail(project)).thenReturn(projectSnapshot);
    when(resourcePersister.getSnapshotOrFail(aPackage)).thenReturn(packageSnapshot);
    when(resourcePersister.getSnapshot(project)).thenReturn(projectSnapshot);
    when(resourcePersister.getSnapshot(aPackage)).thenReturn(packageSnapshot);

    measurePersister = new MeasurePersister(getMyBatis(), resourcePersister, ruleFinder, memoryOptimizer);
  }

  @Test
  public void should_insert_measure() {
    setupData("empty");

    Measure measure = new Measure(ncloc()).setValue(1234.0);
    measurePersister.saveMeasure(project, measure);

    checkTables("shouldInsertMeasure", "project_measures");
    verify(memoryOptimizer).evictDataMeasure(eq(measure), any(MeasureModel.class));
    assertThat(measure.getId()).isNotNull();
  }

  @Test
  public void should_display_contextual_info_when_error_during_insert_measure() {
    setupData("empty");

    Measure measure = new Measure(ncloc()).setValue(1234.0).setAlertText(TOO_LONG);

    thrown.expect(SonarException.class);
    thrown.expectMessage("Unable to save measure for metric [ncloc] on component [foo]");

    measurePersister.saveMeasure(project, measure);
  }

  @Test
  public void should_reload_measure() {
    Measure measure = new Measure(ncloc());

    measurePersister.reloadMeasure(measure);

    verify(memoryOptimizer).reloadMeasure(measure);
  }

  @Test
  public void should_insert_rule_measure() {
    setupData("empty");

    Rule rule = Rule.create("pmd", "key");
    when(ruleFinder.findByKey("pmd", "key")).thenReturn(rule);

    Measure measure = new RuleMeasure(ncloc(), rule, RulePriority.MAJOR, 1).setValue(1234.0);
    measurePersister.saveMeasure(project, measure);

    checkTables("shouldInsertRuleMeasure", "project_measures");
    assertThat(measure.getId()).isNotNull();
  }

  @Test
  public void should_insert_measure_with_text_data() {
    setupData("empty");

    Measure withLargeData = new Measure(ncloc()).setData(LONG);
    measurePersister.saveMeasure(project, withLargeData);

    checkTables("shouldInsertMeasureWithLargeData", "project_measures", "measure_data");

    ArgumentCaptor<MeasureModel> validMeasureModel = ArgumentCaptor.forClass(MeasureModel.class);
    verify(memoryOptimizer).evictDataMeasure(eq(withLargeData), validMeasureModel.capture());
    assertThat(validMeasureModel.getValue().getMeasureData().getId()).isNotNull();
    assertThat(withLargeData.getId()).isNotNull();
  }

  @Test
  public void should_not_save_best_values() {
    setupData("empty");

    measurePersister.saveMeasure(aFile, new Measure(coverage()).setValue(100.0));

    assertEmptyTables("project_measures", "measure_data");
  }

  @Test
  public void should_not_save_memory_only_measures() {
    setupData("empty");

    measurePersister.saveMeasure(aFile, new Measure("ncloc").setPersistenceMode(PersistenceMode.MEMORY));

    assertEmptyTables("project_measures", "measure_data");
  }

  @Test
  public void should_always_save_non_file_measures() {
    setupData("empty");

    measurePersister.saveMeasure(project, new Measure(ncloc()).setValue(200.0));
    measurePersister.saveMeasure(aPackage, new Measure(ncloc()).setValue(300.0));

    checkTables("shouldAlwaysPersistNonFileMeasures", "project_measures");
  }

  @Test
  public void should_update_measure() {
    setupData("data");

    measurePersister.saveMeasure(project, new Measure(coverage()).setValue(12.5).setId(1L));
    measurePersister.saveMeasure(project, new Measure(coverage()).setData(SHORT).setId(2L));
    measurePersister.saveMeasure(aPackage, new Measure(coverage()).setData(LONG).setId(3L));

    checkTables("shouldUpdateMeasure", "project_measures", "measure_data");
  }

  @Test
  public void should_add_delayed_measure_several_times() {
    setupData("empty");

    Measure measure = new Measure(ncloc());

    measurePersister.setDelayedMode(true);
    measurePersister.saveMeasure(project, measure.setValue(200.0));
    measurePersister.saveMeasure(project, measure.setValue(300.0));
    measurePersister.dump();

    checkTables("shouldAddDelayedMeasureSeveralTimes", "project_measures");
  }

  @Test
  public void should_delay_saving() {
    setupData("empty");

    measurePersister.setDelayedMode(true);
    measurePersister.saveMeasure(project, new Measure(ncloc()).setValue(1234.0).setData(SHORT));
    measurePersister.saveMeasure(aPackage, new Measure(ncloc()).setValue(50.0).setData(LONG));

    assertEmptyTables("project_measures");

    measurePersister.dump();
    checkTables("shouldDelaySaving", "project_measures", "measure_data");
  }

  @Test
  public void should_display_contextual_info_when_error_during_delay_saving() {
    setupData("empty");

    measurePersister.setDelayedMode(true);

    measurePersister.saveMeasure(project, new Measure(ncloc()).setValue(1234.0).setData(SHORT).setAlertText(TOO_LONG));

    thrown.expect(SonarException.class);
    thrown.expectMessage("Unable to save measure for metric [ncloc] on component [foo]");

    measurePersister.dump();
  }

  @Test
  public void should_not_delay_saving_with_database_only_measure() {
    setupData("empty");

    measurePersister.setDelayedMode(true);
    measurePersister.saveMeasure(project, new Measure(ncloc()).setValue(1234.0).setPersistenceMode(PersistenceMode.DATABASE));
    measurePersister.saveMeasure(aPackage, new Measure(ncloc()).setValue(50.0));

    checkTables("shouldInsertMeasure", "project_measures");
  }

  @Test
  public void should_not_save_best_value_measures_in_delayed_mode() {
    setupData("empty");

    measurePersister.setDelayedMode(true);
    measurePersister.saveMeasure(aFile, new Measure(coverage()).setValue(100.0));

    assertEmptyTables("project_measures", "measure_data");

    measurePersister.dump();

    assertEmptyTables("project_measures", "measure_data");
  }

  @Test
  public void should_not_save_some_file_measures_with_best_value() {
    assertThat(MeasurePersister.shouldPersistMeasure(aFile, new Measure(CoreMetrics.LINES, 200.0))).isTrue();
    assertThat(MeasurePersister.shouldPersistMeasure(aFile, new Measure(CoreMetrics.DUPLICATED_LINES_DENSITY, 3.0))).isTrue();

    Measure duplicatedLines = new Measure(CoreMetrics.DUPLICATED_LINES_DENSITY, 0.0);
    assertThat(MeasurePersister.shouldPersistMeasure(aFile, duplicatedLines)).isFalse();

    duplicatedLines.setVariation1(0.0);
    assertThat(MeasurePersister.shouldPersistMeasure(aFile, duplicatedLines)).isFalse();

    duplicatedLines.setVariation1(-3.0);
    assertThat(MeasurePersister.shouldPersistMeasure(aFile, duplicatedLines)).isTrue();
  }

  @Test
  public void should_not_save_measures_without_data() {
    assertThat(MeasurePersister.shouldPersistMeasure(aFile, new Measure(CoreMetrics.LINES))).isFalse();

    Measure duplicatedLines = new Measure(CoreMetrics.DUPLICATED_LINES_DENSITY);
    assertThat(MeasurePersister.shouldPersistMeasure(aFile, duplicatedLines)).isFalse();
  }

  private static Snapshot snapshot(int id) {
    Snapshot snapshot = mock(Snapshot.class);
    when(snapshot.getId()).thenReturn(id);
    return snapshot;
  }

  private static Metric ncloc() {
    Metric ncloc = mock(Metric.class);
    when(ncloc.getId()).thenReturn(1);
    when(ncloc.getKey()).thenReturn("ncloc");
    return ncloc;
  }

  private static Metric coverage() {
    Metric coverage = mock(Metric.class);
    when(coverage.getId()).thenReturn(COVERAGE_METRIC_ID);
    when(coverage.getKey()).thenReturn("coverage");
    when(coverage.isOptimizedBestValue()).thenReturn(true);
    when(coverage.getBestValue()).thenReturn(100.0);
    return coverage;
  }
}
