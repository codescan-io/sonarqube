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
import * as classNames from 'classnames';
import { sanitize } from 'dompurify';
import * as React from 'react';
import { ResetButtonLink, SubmitButton } from 'sonar-ui-common/components/controls/buttons';
import Modal from 'sonar-ui-common/components/controls/Modal';
import Select from 'sonar-ui-common/components/controls/Select';
import { Alert } from 'sonar-ui-common/components/ui/Alert';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { activateRule, Profile } from '../../../api/quality-profiles';
import SeverityHelper from '../../../components/shared/SeverityHelper';
import { SEVERITIES } from '../../../helpers/constants';
import { sortProfiles } from '../../quality-profiles/utils';
import { DeleteButton } from 'sonar-ui-common/components/controls/buttons';

interface Props {
  activation?: T.RuleActivation;
  modalHeader: string;
  onClose: () => void;
  onDone: (severity: string) => Promise<void>;
  organization: string | undefined;
  profiles: Profile[];
  rule: T.Rule | T.RuleDetails;
}

interface State {
  params: T.Dict<string | any>;
  allParams: T.Dict<string | any>;
  profile: string;
  severity: string;
  submitting: boolean;
}

export default class ActivationFormModal extends React.PureComponent<Props, State> {
  mounted = false;
  paramsDelimiter = ";";
  keyValueDelimiter = "=";

  constructor(props: Props) {
    super(props);
    const profilesWithDepth = this.getQualityProfilesWithDepth(props);
    this.state = {
      params: this.getDefaultParams(props),
      allParams: this.getAllParams(props),
      profile: profilesWithDepth.length > 0 ? profilesWithDepth[0].key : '',
      severity: props.activation ? props.activation.severity : props.rule.severity,
      submitting: false
    };
  }

  componentDidMount() {
    this.mounted = true;
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  getDefaultParams = ({ activation, rule } = this.props) => {
    const params: T.Dict<any> = {};
    if (rule && rule.params) {
      for (const param of rule.params) {
        if (param.type == "KEY_VALUE_MAP") {
          let paramsArray: String[] = [];
          if (param.defaultValue) {
            paramsArray = param.defaultValue.split(this.paramsDelimiter);
          }
          paramsArray.push(this.keyValueDelimiter);
          params[param.key] = paramsArray.map((object: any) => object.split(this.keyValueDelimiter));
        } else {
          params[param.key] = param.defaultValue || '';
        }
      }
      if (activation && activation.params) {
        for (const param of activation.params) {
          if (typeof (params[param.key]) !== 'string') {
            let paramsArray: String[] = [];
            paramsArray = param.value.split(this.paramsDelimiter);
            paramsArray.push(this.keyValueDelimiter);
            params[param.key] = paramsArray.map((object: any) => object.split(this.keyValueDelimiter));
          } else {
            params[param.key] = param.value || '';
          }
        }
      }
    }
    return params;
  };


  // Unlike other param types, SINGLE_SELECT_LIST has predefined list of values from which user can choose from. 
  getAllParams = ({ rule } = this.props) => {
    const params: T.Dict<any> = {};
    if (rule && rule.params) {
      for (const param of rule.params) {
        if (param.type.startsWith("SINGLE_SELECT_LIST")) {
          let list: String;
          list = param.type.substring(param.type.indexOf('\"') + 1);
          list = list.substring(0, list.indexOf(',\"'));
          params[param.key] = list.split(',');
        }
      }
    }
    return params;
  };

  getParamsFromMap = () => {
    let stateParams = { ...this.state.params };
    const { params = [] } = this.props.rule;
    params.map(param => {
      if (param.type == 'KEY_VALUE_MAP') {
        stateParams[param.key].pop();
        let res = '';
        let row = stateParams[param.key].length;
        stateParams[param.key].map((param: string[], index: number) => {
          res = res + param[0] + this.keyValueDelimiter + param[1];
          if (index + 1 != row) {
            res = res + this.paramsDelimiter;
          }
        })
        stateParams[param.key] = res;
      }
    })
    return stateParams;
  }

  // Choose QP which a user can administrate, which are the same language and which are not built-in
  getQualityProfilesWithDepth = ({ profiles } = this.props) => {
    return sortProfiles(
      profiles.filter(
        profile =>
          !profile.isBuiltIn &&
          profile.actions &&
          profile.actions.edit &&
          profile.language === this.props.rule.lang
      )
    ).map(profile => ({
      ...profile,
      // Decrease depth by 1, so the top level starts at 0
      depth: profile.depth - 1
    }));
  };

  handleFormSubmit = (event: React.SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    this.setState({ submitting: true });
    const data = {
      key: this.state.profile,
      organization: this.props.organization,
      params: this.getParamsFromMap(),
      rule: this.props.rule.key,
      severity: this.state.severity
    };
    activateRule(data)
      .then(() => this.props.onDone(data.severity))
      .then(
        () => {
          if (this.mounted) {
            this.setState({ submitting: false });
            this.props.onClose();
          }
        },
        () => {
          if (this.mounted) {
            this.setState({ submitting: false });
          }
        }
      );
  };

  handleParameterChange = (event: React.SyntheticEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = event.currentTarget;
    this.setState((state: State) => ({ params: { ...state.params, [name]: value } }));
  };

  handleSingleSelectListChange = ({ value, labelKey }: { label: string, value: string, labelKey: string }) => {
    let params = { ...this.state.params };
    params[labelKey] = value;
    this.setState({ params });
  };

  handleProfileChange = ({ value }: { value: string }) => {
    this.setState({ profile: value });
  };

  handleSeverityChange = ({ value }: { value: string }) => {
    this.setState({ severity: value });
  };

  renderSeverityOption = ({ value }: { value: string }) => {
    return <SeverityHelper severity={value} />;
  };

  handleKeyChange = (index: any, value: any, paramKey: any) => {
    let params = { ...this.state.params };
    params[paramKey][index][0] = value;
    if (index === params[paramKey].length - 1) {
      params[paramKey].splice(index + 1, 0, ['', '']);
    }
    this.setState({ params });
  };

  handleValueChange = (index: any, value: any, paramKey: any) => {
    let params = { ...this.state.params };
    params[paramKey][index][1] = value;
    if (index === params[paramKey].length - 1) {
      params[paramKey].splice(index + 1, 0, ['', '']);
    }
    this.setState({ params });
  };

  handleDeleteValue = (index: number, paramKey: any) => {
    let params = { ...this.state.params };
    params[paramKey].splice(index, 1);
    this.setState({ params });
  };


  render() {
    const { activation, rule } = this.props;
    const { profile, severity, submitting } = this.state;
    const { params = [] } = rule;
    const profilesWithDepth = this.getQualityProfilesWithDepth();
    const isCustomRule = !!(rule as T.RuleDetails).templateKey;
    const activeInAllProfiles = profilesWithDepth.length <= 0;
    const isUpdateMode = !!activation;

    return (
      <Modal contentLabel={this.props.modalHeader} onRequestClose={this.props.onClose} size="small">
        <form onSubmit={this.handleFormSubmit}>
          <div className="modal-head">
            <h2>{this.props.modalHeader}</h2>
          </div>

          <div className={classNames('modal-body', { 'modal-container': params.length > 0 })}>
            {!isUpdateMode && activeInAllProfiles && (
              <Alert variant="info">{translate('coding_rules.active_in_all_profiles')}</Alert>
            )}

            <div className="modal-field">
              <label>{translate('coding_rules.quality_profile')}</label>
              <Select
                className="js-profile"
                clearable={false}
                disabled={submitting || profilesWithDepth.length === 1}
                onChange={this.handleProfileChange}
                options={profilesWithDepth.map(profile => ({
                  label: '   '.repeat(profile.depth) + profile.name,
                  value: profile.key
                }))}
                value={profile}
              />
            </div>
            <div className="modal-field">
              <label>{translate('severity')}</label>
              <Select
                className="js-severity"
                clearable={false}
                disabled={submitting}
                onChange={this.handleSeverityChange}
                optionRenderer={this.renderSeverityOption}
                options={SEVERITIES.map(severity => ({
                  label: translate('severity', severity),
                  value: severity
                }))}
                searchable={false}
                value={severity}
                valueRenderer={this.renderSeverityOption}
              />
            </div>
            {isCustomRule ? (
              <div className="modal-field">
                <p className="note">{translate('coding_rules.custom_rule.activation_notice')}</p>
              </div>
            ) : (
              params.map(param => (
                <div className="modal-field" key={param.key}>
                  <label title={param.key}>{param.key}</label>
                  {param.type === 'TEXT' &&
                    (
                      <textarea
                        disabled={submitting}
                        name={param.key}
                        onChange={this.handleParameterChange}
                        placeholder={param.defaultValue}
                        rows={3}
                        value={this.state.params[param.key] || ''}
                      />
                    )
                  }
                  {param.type.startsWith('SINGLE_SELECT_LIST') &&
                    (
                      <Select
                        className="js-list"
                        clearable={false}
                        onChange={this.handleSingleSelectListChange}
                        options={this.state.allParams[param.key].map((item: string) => ({
                          labelKey: param.key,
                          label: item,
                          value: item
                        }))}
                        value={this.state.params[param.key] || ''}
                      />
                    )
                  }
                  {param.type === 'KEY_VALUE_MAP' &&
                    (
                      <ul>
                        {this.state.params[param.key].map((value: any, index: number) =>
                          <li className="spacer-bottom display-flex-row " key={index}>
                            <input
                              disabled={submitting}
                              className="coding-rule-map-input"
                              onChange={e => this.handleKeyChange(index, e.target.value, param.key)}
                              name={"key" + index}
                              type="text"
                              value={value[0]} />

                            <input
                              disabled={submitting}
                              className="coding-rule-map-input"
                              onChange={e => this.handleValueChange(index, e.target.value, param.key)}
                              name={"value" + index}
                              type="text"
                              value={value[1]} />

                            {!(index === this.state.params[param.key].length - 1) && (
                              <div className="display-inline-block spacer-left">
                                <DeleteButton
                                  className="js-remove-value"
                                  onClick={() => this.handleDeleteValue(index, param.key)}
                                />
                              </div>
                            )}
                          </li>
                        )}
                      </ul>
                    )
                  }
                  {param.type !== 'KEY_VALUE_MAP' && !param.type.startsWith('SINGLE_SELECT_LIST') && param.type !== 'TEXT' &&
                    (
                      <input
                        disabled={submitting}
                        name={param.key}
                        onChange={this.handleParameterChange}
                        placeholder={param.defaultValue}
                        type="text"
                        value={this.state.params[param.key] || ''}
                      />
                    )
                  }
                  <div
                    className="note"
                    // eslint-disable-next-line react/no-danger
                    dangerouslySetInnerHTML={{ __html: sanitize(param.htmlDesc || '') }}
                  />
                </div>
              ))
            )}
          </div>

          <footer className="modal-foot">
            {submitting && <i className="spinner spacer-right" />}
            <SubmitButton disabled={submitting || activeInAllProfiles}>
              {isUpdateMode ? translate('save') : translate('coding_rules.activate')}
            </SubmitButton>
            <ResetButtonLink disabled={submitting} onClick={this.props.onClose}>
              {translate('cancel')}
            </ResetButtonLink>
          </footer>
        </form>
      </Modal>
    );
  }
}
