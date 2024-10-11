/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
import { BasicSeparator, Card, DeferredSpinner } from 'design-system';
import * as React from 'react';
import { translate } from '../../../helpers/l10n';
import { ComponentQualifier, isApplication } from '../../../types/component';
import { QualityGateStatus } from '../../../types/quality-gates';
import { CaycStatus, Component } from '../../../types/types';
import IgnoredConditionWarning from '../components/IgnoredConditionWarning';
import QualityGateStatusHeader from '../components/QualityGateStatusHeader';
import QualityGateStatusPassedView from '../components/QualityGateStatusPassedView';
import { QualityGateStatusTitle } from '../components/QualityGateStatusTitle';
import ApplicationNonCaycProjectWarning from './ApplicationNonCaycProjectWarning';
import QualityGatePanelSection from './QualityGatePanelSection';

export interface QualityGatePanelProps {
  component: Pick<Component, 'key' | 'qualifier' | 'qualityGate'>;
  loading?: boolean;
  qgStatuses?: QualityGateStatus[];
  grc: boolean;
}

export function QualityGatePanel(props: QualityGatePanelProps) {
  const { component, loading, qgStatuses = [], grc } = props;

  if (qgStatuses === undefined) {
    return null;
  }

  const overallLevel = qgStatuses.map((s) => s.status).includes('ERROR') ? 'ERROR' : 'OK';
  const success = overallLevel === 'OK';

  const overallFailedConditionsCount = qgStatuses.reduce(
    (acc, qgStatus) => acc + qgStatus.failedConditions.length,
    0
  );

  const nonCaycProjectsInApp = isApplication(component.qualifier)
    ? qgStatuses
        .filter(({ caycStatus }) => caycStatus === CaycStatus.NonCompliant)
        .sort(({ name: a }, { name: b }) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
    : [];

  const overCompliantCaycProjectsInApp = isApplication(component.qualifier)
    ? qgStatuses
        .filter(({ caycStatus }) => caycStatus === CaycStatus.OverCompliant)
        .sort(({ name: a }, { name: b }) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
    : [];

  const showIgnoredConditionWarning =
    component.qualifier === ComponentQualifier.Project &&
    qgStatuses.some((p) => Boolean(p.ignoredConditions));

  return (
    <div data-test="overview__quality-gate-panel">
      <QualityGateStatusTitle grc={grc} />
      <Card>
        <div>
          {loading ? (
            <div className="sw-p-6">
              <DeferredSpinner loading={loading} />
            </div>
          ) : (
            <>
              <QualityGateStatusHeader
                status={overallLevel}
                failedConditionCount={overallFailedConditionsCount}
                grc={grc}
              />
              {success && <QualityGateStatusPassedView />}

              {showIgnoredConditionWarning && <IgnoredConditionWarning />}

              {!success && <BasicSeparator />}

              {(overallFailedConditionsCount > 0 ||
                qgStatuses.some(({ caycStatus }) => caycStatus !== CaycStatus.Compliant)) && (
                <div data-test="overview__quality-gate-conditions">
                  {qgStatuses.map((qgStatus) => (
                    <QualityGatePanelSection
                      component={component}
                      key={qgStatus.key}
                      qgStatus={qgStatus}
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </Card>

      {nonCaycProjectsInApp.length > 0 && (
        <ApplicationNonCaycProjectWarning
          projects={nonCaycProjectsInApp}
          caycStatus={CaycStatus.NonCompliant}
        />
      )}

      {overCompliantCaycProjectsInApp.length > 0 && (
        <ApplicationNonCaycProjectWarning
          projects={overCompliantCaycProjectsInApp}
          caycStatus={CaycStatus.OverCompliant}
        />
      )}
    </div>
  );
}

export default React.memo(QualityGatePanel);
