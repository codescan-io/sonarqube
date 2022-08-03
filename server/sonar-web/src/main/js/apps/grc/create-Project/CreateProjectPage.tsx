/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
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
import { FormattedMessage } from 'react-intl';
import { Link, WithRouterProps } from 'react-router';
import { ResetButtonLink, SubmitButton } from 'sonar-ui-common/components/controls/buttons';
import { Alert } from 'sonar-ui-common/components/ui/Alert';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { createProject, setProjectTags } from '../../../api/components';
import VisibilitySelector from '../../../components/common/VisibilitySelector';
import { getProjectUrl } from '../../../helpers/urls';

interface Props {
  //onProjectCreated: () => void;
  onOrganizationUpgrade: () => void;
  organization: T.Organization;
}

interface State {
  createdProject?: { key: string; name: string };
  key: string;
  loading: boolean;
  name: string;
  visibility?: T.Visibility;
  // add index declaration to be able to do `this.setState({ [name]: value });`
  [x: string]: any;
}

export default class CreateProjectPage extends React.PureComponent<Props & WithRouterProps, State> {
  closeButton?: HTMLElement | null;
  mounted = false;

  constructor(props: Props & WithRouterProps) {
    super(props);
    this.state = {
      key: '',
      loading: false,
      name: '',
      //visibility: props.organization.projectVisibility
    };
  }

  componentDidMount() {
    this.mounted = true;
  }

  componentDidUpdate() {
    // wrap with `setImmediate` because of https://github.com/reactjs/react-modal/issues/338
    setImmediate(() => {
      if (this.closeButton) {
        this.closeButton.focus();
      }
    });
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  handleInputChange = (event: React.SyntheticEvent<HTMLInputElement>) => {
    const { name, value } = event.currentTarget;
    this.setState({ [name]: value });
  };

  handleVisibilityChange = (visibility: T.Visibility) => {
    this.setState({ visibility });
  };

  handleFormSubmit = (event: React.SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();

    const data = {
      name: this.state.name,
      organization: this.props.organization && this.props.organization.key,
      project: this.state.key,
      visibility: this.state.visibility
    };

    this.setState({ loading: true });
   // associateProject({ language: “sfmeta”, name: “GRC”, organization}, project);

    createProject(data).then(
      response => {
        if (this.mounted) {
          this.setState({ createdProject: response.project, loading: false });
          setProjectTags({ project: data.project, tags: "grc" });
          //this.props.onProjectCreated();
        }
      },
      () => {
        if (this.mounted) {
          this.setState({ loading: false });
        }
      }
    );
  };

  handleClose = () => {
    this.props.router.replace('/grc');
  }

  render() {
    const { organization } = this.props;
    const { createdProject } = this.state;

    return (
      <div>
        {createdProject ? (
          <div>
            <header>
              <h2>{translate('qualifiers.create.TRK')}</h2>
            </header>

            <div>
              <Alert variant="success">
                <FormattedMessage
                  defaultMessage={translate(
                    'projects_management.project_has_been_successfully_created'
                  )}
                  id="projects_management.project_has_been_successfully_created"
                  values={{
                    project: (
                      <Link to={getProjectUrl(createdProject.key)}>{createdProject.name}</Link>
                    )
                  }}
                />
              </Alert>
            </div>

            <footer>
              <ResetButtonLink
                id="create-project-close"
                innerRef={node => (this.closeButton = node)}
                >
                {translate('close')}
              </ResetButtonLink>
            </footer>
          </div>
        ) : (
          <form id="create-project-form" onSubmit={this.handleFormSubmit}>
            <header>
              <h2>{translate('qualifiers.create.TRK')}</h2>
            </header>

            <div>
              <div>
                <label htmlFor="create-project-name">
                  {translate('name')}
                  <em className="mandatory">*</em>
                </label>
                <input
                  autoFocus={true}
                  id="create-project-name"
                  maxLength={2000}
                  name="name"
                  onChange={this.handleInputChange}
                  required={true}
                  type="text"
                  value={this.state.name}
                />
              </div>
              <div>
                <label htmlFor="create-project-key">
                  {translate('key')}
                  <em className="mandatory">*</em>
                </label>
                <input
                  id="create-project-key"
                  maxLength={400}
                  name="key"
                  onChange={this.handleInputChange}
                  required={true}
                  type="text"
                  value={this.state.key}
                />
              </div>
              <div>
                <label>{translate('visibility')}</label>
                <VisibilitySelector
                  canTurnToPrivate={false}
                  className="little-spacer-top"
                  onChange={this.handleVisibilityChange}
                  visibility={this.state.visibility}
                />
              </div>
            </div>

            <footer>
              {this.state.loading && <i className="spinner spacer-right" />}
              <SubmitButton disabled={this.state.loading} id="create-project-submit">
                {translate('create')}
              </SubmitButton>
              <ResetButtonLink id="create-project-cancel" onClick={this.handleClose}>
                {translate('cancel')}
              </ResetButtonLink>
            </footer>
          </form>
        )}
      </div>
    );
  }
}
