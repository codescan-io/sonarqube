/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.ce;

import java.time.Duration;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CodeScan: covers the eligibility predicate that defers {@link CeTaskTypes#RESOLUTION_PROLIFERATION} behind a
 * project's other pending work.
 * <p>
 * The predicate lives entirely in SQL, so nothing above the DAO can test it: a mocked mapper returns whatever it
 * is told. These tests are what proves the three properties the predicate claims - that proliferation yields,
 * that it yields only to its own project, and that the wait is bounded - and, just as importantly, that every
 * other task type is picked exactly as it was before.
 */
public class CeQueueDaoProliferationIT {

  private static final long FRESH_AGE_MS = Duration.ofMinutes(1).toMillis();
  private static final long STALE_AGE_MS = Duration.ofHours(2).toMillis();

  private static final String PROJECT_A = "project-a-uuid";
  private static final String PROJECT_B = "project-b-uuid";

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private final DbSession dbSession = db.getSession();
  private final CeQueueDao underTest = db.getDbClient().ceQueueDao();

  private final long now = System2.INSTANCE.now();

  @Test
  public void defersProliferationWhileItsOwnProjectHasOtherPendingWork() {
    insert("proliferation", PROJECT_A, CeTaskTypes.RESOLUTION_PROLIFERATION, now - FRESH_AGE_MS);
    insert("report", PROJECT_A, CeTaskTypes.REPORT, now - FRESH_AGE_MS / 2);

    // The proliferation task is the older of the two, so ordering alone would have picked it.
    assertThat(peek()).contains("report");
  }

  @Test
  public void picksProliferationWhenItsProjectHasNothingElsePending() {
    insert("proliferation", PROJECT_A, CeTaskTypes.RESOLUTION_PROLIFERATION, now - FRESH_AGE_MS);

    assertThat(peek()).contains("proliferation");
  }

  @Test
  public void doesNotDeferProliferationBehindAnotherProjectsPendingWork() {
    insert("proliferation", PROJECT_A, CeTaskTypes.RESOLUTION_PROLIFERATION, now - FRESH_AGE_MS);
    insert("report", PROJECT_B, CeTaskTypes.REPORT, now - FRESH_AGE_MS / 2);

    assertThat(peek()).contains("proliferation");
  }

  @Test
  public void doesNotDeferProliferationBehindAnotherProliferationOnTheSameProject() {
    insert("older", PROJECT_A, CeTaskTypes.RESOLUTION_PROLIFERATION, now - FRESH_AGE_MS);
    insert("newer", PROJECT_A, CeTaskTypes.RESOLUTION_PROLIFERATION, now - FRESH_AGE_MS / 2);

    assertThat(peek()).contains("older");
  }

  @Test
  public void picksProliferationOnceItHasWaitedLongerThanTheDeferralWindow() {
    insert("proliferation", PROJECT_A, CeTaskTypes.RESOLUTION_PROLIFERATION, now - STALE_AGE_MS);
    insert("report", PROJECT_A, CeTaskTypes.REPORT, now - FRESH_AGE_MS);

    // Without the age escape a project analysed on a short cycle would hold this task for ever.
    assertThat(peek()).contains("proliferation");
  }

  @Test
  public void leavesEveryOtherTaskTypeToBePickedOnAgeAlone() {
    insert("report", PROJECT_A, CeTaskTypes.REPORT, now - STALE_AGE_MS);
    insert("audit", PROJECT_A, CeTaskTypes.AUDIT_PURGE, now - FRESH_AGE_MS);

    // Neither is RESOLUTION_PROLIFERATION, so the first branch of the condition is true for both and nothing
    // after it is evaluated: they queue against each other exactly as they did before the predicate existed,
    // oldest first, even though they share a project.
    assertThat(peek()).contains("report");
  }

  private Optional<String> peek() {
    return underTest.selectEligibleForPeek(dbSession, false, false).map(CeTaskDtoLight::getCeTaskUuid);
  }

  private void insert(String uuid, String entityUuid, String taskType, long createdAt) {
    underTest.insert(dbSession, new CeQueueDto()
      .setUuid(uuid)
      .setTaskType(taskType)
      .setComponentUuid(entityUuid)
      .setEntityUuid(entityUuid)
      .setStatus(CeQueueDto.Status.PENDING)
      .setCreatedAt(createdAt)
      .setUpdatedAt(createdAt));
    dbSession.commit();
  }
}
