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
import * as React from 'react';
import { Helmet } from 'react-helmet-async';
import { NavigateFunction, useNavigate, useParams } from 'react-router-dom';
import { fetchQualityGates } from '../../../api/quality-gates';
import ScreenPositionHelper from '../../../components/common/ScreenPositionHelper';
import Suggestions from '../../../components/embed-docs-modal/Suggestions';
import '../../../components/search-navigator.css';
import DeferredSpinner from '../../../components/ui/DeferredSpinner';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import {
  addSideBarClass,
  addWhitePageClass,
  removeSideBarClass,
  removeWhitePageClass,
} from '../../../helpers/pages';
import { getQualityGateUrl } from '../../../helpers/urls';
import { Organization, QualityGate } from '../../../types/types';
import '../styles.css';
import Details from './Details';
import List from './List';
import ListHeader from './ListHeader';
import { withOrganizationContext } from "../../organizations/OrganizationContext";

interface Props {
  name?: string;
  navigate: NavigateFunction;
  organization: Organization;
}

interface State {
  canCreate: boolean;
  loading: boolean;
  qualityGates: QualityGate[];
}

class App extends React.PureComponent<Props, State> {
  mounted = false;
  state: State = { canCreate: false, loading: true, qualityGates: [] };

  componentDidMount() {
    this.mounted = true;
    this.fetchQualityGates();
    addWhitePageClass();
    addSideBarClass();
  }

  componentDidUpdate(prevProps: Props) {
    if (prevProps.name !== undefined && this.props.name === undefined) {
      this.openDefault(this.state.qualityGates);
    }
  }

  componentWillUnmount() {
    this.mounted = false;
    removeWhitePageClass();
    removeSideBarClass();
  }

  fetchQualityGates = () => {
    return fetchQualityGates({ organization: this.props.organization.kee }).then(
      ({ actions, qualitygates: qualityGates }) => {
        if (this.mounted) {
          this.setState({ canCreate: actions.create, loading: false, qualityGates });

          if (!this.props.name) {
            this.openDefault(qualityGates);
          }
        }
      },
      () => {
        if (this.mounted) {
          this.setState({ loading: false });
        }
      }
    );
  };

  openDefault(qualityGates: QualityGate[]) {
    const defaultQualityGate = qualityGates.find((gate) => Boolean(gate.isDefault))!;
    this.props.navigate(getQualityGateUrl(this.props.organization.kee, String(defaultQualityGate.name)), { replace: true });
  }

  handleSetDefault = (qualityGate: QualityGate) => {
    this.setState(({ qualityGates }) => {
      return {
        qualityGates: qualityGates.map((candidate) => {
          if (candidate.isDefault || candidate.name === qualityGate.name) {
            return { ...candidate, isDefault: candidate.name === qualityGate.name };
          }
          return candidate;
        }),
      };
    });
  };

  render() {
    const { name } = this.props;
    const { canCreate, qualityGates } = this.state;

    return (
      <>
        <Helmet
          defer={false}
          titleTemplate={translateWithParameters(
            'page_title.template.with_category',
            translate('quality_gates.page')
          )}
        />
        <div className="layout-page" id="quality-gates-page">
          <Suggestions suggestions="quality_gates" />

          <ScreenPositionHelper className="layout-page-side-outer">
            {({ top }) => (
              <nav className="layout-page-side" style={{ top }}>
                <div className="layout-page-side-inner">
                  <div className="layout-page-filters">
                    <ListHeader
                      canCreate={canCreate}
                      refreshQualityGates={this.fetchQualityGates}
                      organization={this.props.organization.kee}
                    />
                    <DeferredSpinner loading={this.state.loading}>
                      <List
                        organization={this.props.organization.kee}
                        qualityGates={qualityGates}
                        currentQualityGate={name}
                      />
                    </DeferredSpinner>
                  </div>
                </div>
              </nav>
            )}
          </ScreenPositionHelper>

          {name !== undefined && (
            <Details
              organization={this.props.organization.kee}
              qualityGateName={name}
              onSetDefault={this.handleSetDefault}
              qualityGates={this.state.qualityGates}
              refreshQualityGates={this.fetchQualityGates}
            />
          )}
        </div>
      </>
    );
  }
}

export interface AppWrapperProps {
  organization: Organization;
}

function AppWrapper(props: AppWrapperProps) {
  const params = useParams();
  const navigate = useNavigate();

  return <App name={params['name']} navigate={navigate} organization={props.organization}/>;
}

export default withOrganizationContext(AppWrapper);
