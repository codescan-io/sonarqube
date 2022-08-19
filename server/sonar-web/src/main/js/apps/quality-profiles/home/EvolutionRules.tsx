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
import { sortBy } from 'lodash';
import * as React from 'react';
import { Link } from 'react-router';
import { toShortNotSoISOString } from 'sonar-ui-common/helpers/dates';
import { translate, translateWithParameters } from 'sonar-ui-common/helpers/l10n';
import { formatMeasure } from 'sonar-ui-common/helpers/measures';
import { searchRules } from '../../../api/rules';
import { getRulesUrl } from '../../../helpers/urls';

const RULES_LIMIT = 10;

function parseRules(rules: T.Rule[], actives?: T.Dict<T.RuleActivation[]>): ExtendedRule[] {
  return rules.map(rule => {
    const activations = actives && actives[rule.key];
    return { ...rule, activations: activations ? activations.length : 0 };
  });
}

interface Props {
  organization: string | null;
  grc?:boolean,
  componentKey?:string
}

interface ExtendedRule extends T.Rule {
  activations: number;
}

interface State {
  latestRules?: ExtendedRule[];
  latestRulesTotal?: number;
}

export default class EvolutionRules extends React.PureComponent<Props, State> {
  periodStartDate: string;
  mounted = false;

  constructor(props: Props) {
    super(props);
    this.state = {};
    const startDate = new Date();
    startDate.setFullYear(startDate.getFullYear() - 1);
    this.periodStartDate = toShortNotSoISOString(startDate);
  }

  componentDidMount() {
    this.mounted = true;
    this.loadLatestRules();
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  loadLatestRules() {
    const data = {
      asc: false,
      available_since: this.periodStartDate,
      f: 'name,langName,actives',
      organization: this.props.organization || undefined,
      ps: RULES_LIMIT,
      s: 'createdAt'
    };

    searchRules(data).then(
      ({ actives, rules, total }) => {
        if (this.mounted) {
          this.setState({
            latestRules: sortBy(parseRules(rules, actives), 'langName'),
            latestRulesTotal: total
          });
        }
      },
      () => {}
    );
  }

  getParams(ruleKey:string){
    let params:any = {
      rule_key: ruleKey
    }
    if(this.props.grc && this.props.componentKey && this.props.componentKey.length){
      params.id = this.props.componentKey
    }
    return params;
  }

  render() {
    if (!this.state.latestRulesTotal || !this.state.latestRules) {
      return null;
    }

    let moreParams:any = { available_since: this.periodStartDate };

    if(this.props.grc && this.props.componentKey && this.props.componentKey.length){
      moreParams.id = this.props.componentKey
    }
    
    const newRulesUrl = getRulesUrl(
      moreParams,
      this.props.organization,
      this.props.grc
    );

    return (
      <div className="boxed-group boxed-group-inner quality-profiles-evolution-rules">
        <div className="clearfix">
          <strong className="pull-left">{translate('quality_profiles.latest_new_rules')}</strong>
        </div>
        <ul>
          {this.state.latestRules.map(rule => (
            <li className="spacer-top" key={rule.key}>
              <div className="text-ellipsis">
                <Link
                  className="link-no-underline"
                  to={getRulesUrl(this.getParams(rule.key), this.props.organization, this.props.grc)}>
                  {' '}
                  {rule.name}
                </Link>
                <div className="note">
                  {rule.activations
                    ? translateWithParameters(
                        'quality_profiles.latest_new_rules.activated',
                        rule.langName!,
                        rule.activations
                      )
                    : translateWithParameters(
                        'quality_profiles.latest_new_rules.not_activated',
                        rule.langName!
                      )}
                </div>
              </div>
            </li>
          ))}
        </ul>
        {this.state.latestRulesTotal > RULES_LIMIT && (
          <div className="spacer-top">
            <Link className="small" to={newRulesUrl}>
              {translate('see_all')} {formatMeasure(this.state.latestRulesTotal, 'SHORT_INT', null)}
            </Link>
          </div>
        )}
      </div>
    );
  }
}
