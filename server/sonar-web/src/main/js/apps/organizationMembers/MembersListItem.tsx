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
import RemoveMemberForm from './RemoveMemberForm';
import ManageMemberGroupsForm from './ManageMemberGroupsForm';
import Avatar from '../../components/ui/Avatar';
import {translate, translateWithParameters} from "../../helpers/l10n";
import {formatMeasure} from "../../helpers/measures";
import ActionsDropdown, {ActionsDropdownDivider, ActionsDropdownItem} from "../../components/controls/ActionsDropdown";
import { Group, Organization, OrganizationMember } from "../../types/types";
import { setMemberType } from '../../api/organizations';

const USER_TYPES = [
  { value: "STANDARD", label: "Standard User" },
  { value: "PLATFORM", label: "Platform Integration User" }
];

interface Props {
  member: OrganizationMember;
  organization: Organization;
  organizationGroups: Group[];
  removeMember?: (member: OrganizationMember) => void;
  updateMemberGroups: (
    member: OrganizationMember,
    add: string[],
    remove: string[]
  ) => Promise<void>;
}

interface State {
  removeMemberForm: boolean;
  manageGroupsForm: boolean;
  type: string;
}

const AVATAR_SIZE = 36;

export default class MembersListItem extends React.PureComponent<Props, State> {
  mounted = false;
  state: State = { removeMemberForm: false, manageGroupsForm: false, type: this.props.member.type };

  componentDidMount() {
    this.mounted = true;
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  handleManageGroupsClick = () => {
    this.setState({ manageGroupsForm: true });
  };

  closeManageGroupsForm = () => {
    if (this.mounted) {
      this.setState({ manageGroupsForm: false });
    }
  };

  handleRemoveMemberClick = () => {
    this.setState({ removeMemberForm: true });
  };

  closeRemoveMemberForm = () => {
    if (this.mounted) {
      this.setState({ removeMemberForm: false });
    }
  };

  handleRadioChange = async (login: string, type: string) => {
      await setMemberType(this.props.organization.kee, login, type); // API Call
      this.setState({ type });
  };

  render() {
    const { member, organization, removeMember } = this.props;
    const { actions = {} } = organization;
    const { type } = this.state;
    return (
      <tr>
        <td className="thin nowrap">
          <Avatar hash={member.avatar} name={member.name} size={AVATAR_SIZE} />
        </td>
        <td className="nowrap text-middle">
          <strong>{member.name}</strong>
          <span className="note little-spacer-left">{member.login}</span>
        </td>
        {USER_TYPES.map(({ value, label }) => (
          <td key={value} className="nowrap text-middle">
            <input
              type="radio"
              name={member.login}
              value={value}
              className={`member-type-${member.type}`}
              checked={type === value}
              onChange={actions.admin ? () => this.handleRadioChange(member.login, value) : undefined}
            />
            <span className='note little-spacer-left'>{label}</span>
          </td>
        ))}
        {actions.admin && (
          <td className="text-right text-middle">
            {translateWithParameters(
              'organization.members.x_groups',
              formatMeasure(member.groupCount || 0, 'INT')
            )}
          </td>
        )}
        {actions.admin && (
          <>
            <td className="nowrap text-middle text-right">
              <ActionsDropdown>
                <ActionsDropdownItem onClick={this.handleManageGroupsClick}>
                  {translate('organization.members.manage_groups')}
                </ActionsDropdownItem>
                {removeMember && (
                  <>
                    <ActionsDropdownDivider />
                    <ActionsDropdownItem destructive={true} onClick={this.handleRemoveMemberClick}>
                      {translate('organization.members.remove')}
                    </ActionsDropdownItem>
                  </>
                )}
              </ActionsDropdown>
            </td>

            {this.state.manageGroupsForm && (
              <ManageMemberGroupsForm
                member={this.props.member}
                onClose={this.closeManageGroupsForm}
                organization={this.props.organization}
                organizationGroups={this.props.organizationGroups}
                updateMemberGroups={this.props.updateMemberGroups}
              />
            )}

            {removeMember &&
              this.state.removeMemberForm && (
                <RemoveMemberForm
                  member={this.props.member}
                  onClose={this.closeRemoveMemberForm}
                  organization={this.props.organization}
                  removeMember={removeMember}
                />
              )}
          </>
        )}
      </tr>
    );
  }
}
