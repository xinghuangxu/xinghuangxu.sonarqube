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
package org.sonar.core.measure;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Test;
import org.sonar.api.config.Settings;
import org.sonar.core.profiling.Profiling;

import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MeasureFilterEngineTest {

  @Test
  public void should_create_and_execute_filter() throws Exception {
    Map<String, Object> filterMap = ImmutableMap.of("qualifiers", (Object) "TRK");
    MeasureFilterFactory factory = mock(MeasureFilterFactory.class);
    MeasureFilter filter = new MeasureFilter();
    when(factory.create(filterMap)).thenReturn(filter);
    MeasureFilterExecutor executor = mock(MeasureFilterExecutor.class);

    MeasureFilterEngine engine = new MeasureFilterEngine(factory, executor, new Profiling(new Settings()));

    final long userId = 50L;
    engine.execute(filterMap, userId);
    verify(executor).execute(refEq(filter), argThat(new BaseMatcher<MeasureFilterContext>() {
      public boolean matches(Object o) {
        MeasureFilterContext context = (MeasureFilterContext) o;
        return "{qualifiers=TRK}".equals(context.getData()) && context.getUserId() == userId;
      }

      public void describeTo(Description description) {
      }
    }));
  }

  @Test
  public void keep_error_but_do_not_fail() throws Exception {
    Map<String, Object> filterMap = ImmutableMap.of("qualifiers", (Object) "TRK");
    MeasureFilterFactory factory = mock(MeasureFilterFactory.class);
    when(factory.create(filterMap)).thenThrow(new IllegalArgumentException());
    MeasureFilterExecutor executor = mock(MeasureFilterExecutor.class);

    MeasureFilterEngine engine = new MeasureFilterEngine(factory, executor, new Profiling(new Settings()));
    MeasureFilterResult result = engine.execute(filterMap, 50L);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getError()).isEqualTo(MeasureFilterResult.Error.UNKNOWN);
    assertThat(result.getRows()).isNull();
  }
}