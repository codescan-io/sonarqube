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
import * as React from 'react';
import { Table } from '~design-system';
import Avatar from '../../components/ui/Avatar';
import ListFooter from '../../components/controls/ListFooter';
import { getPendingInvitations, PendingInvitation } from '../../api/organizations';
import { Organization, Paging } from '../../types/types';
import './MembersList.css';

interface Props {
  organization: Organization;
}

const PAGE_SIZE = 50;
const AVATAR_SIZE = 36;

export default function PendingInvitations({ organization }: Props) {
  const [invitations, setInvitations] = React.useState<PendingInvitation[]>([]);
  const [paging, setPaging] = React.useState<Paging>();
  const [loading, setLoading] = React.useState<boolean>(false);

  const fetchInvitations = (page?: number) => {
    setLoading(true);
    getPendingInvitations({
      organizationKee: organization.kee,
      p: page || 1,
      ps: PAGE_SIZE,
    }).then(
      ({ paging, invitations: newInvitations }) => {
        setLoading(false);
        if (page && page > 1) {
          setInvitations((prev) => [...prev, ...newInvitations]);
        } else {
          setInvitations(newInvitations);
        }
        setPaging(paging);
      },
      () => setLoading(false),
    );
  };

  React.useEffect(() => {
    if (organization.actions && organization.actions.admin) {
      fetchInvitations();
    }
  }, []);

  const handleLoadMore = () => {
    if (paging) {
      fetchInvitations(paging.pageIndex + 1);
    }
  };

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  const getUserTypeLabel = (userType: string) => {
    return userType === 'PLATFORM' ? 'Platform Integration User' : 'Standard User';
  };

  const getNameFromEmail = (email: string) => {
    const localPart = email.split('@')[0];
    return localPart
      .replace(/[._-]/g, ' ')
      .replace(/\b\w/g, (c) => c.toUpperCase());
  };

  if (!organization.actions?.admin || invitations.length === 0) {
    return null;
  }

  return (
    <div className="sw-mt-16 sw-ml-16 sw-mr-8">
      <h2 className="page-title">Pending Invitations</h2>
      <div className="boxed-group boxed-group-inner">
        <Table className="data zebra members-list-table">
          <tr>
            <td className="thin nowrap"></td>
            <td className="nowrap text-middle" style={{ width: '40%' }}><strong>Name</strong></td>
            <td className="nowrap text-middle"><strong>User Type</strong></td>
            <td className="nowrap text-middle"><strong>Invited On</strong></td>
            <td className="nowrap text-middle"><strong>Status</strong></td>
          </tr>
          {invitations.map((invitation) => (
            <tr key={invitation.id}>
              <td className="thin nowrap">
                <Avatar
                  className="sw-p-2"
                  name={getNameFromEmail(invitation.email)}
                  size={AVATAR_SIZE}
                />
              </td>
              <td className="nowrap text-middle" style={{ width: '40%' }}>
                <strong>{getNameFromEmail(invitation.email)}</strong>
                <span className="note sw-ml-2">{invitation.email}</span>
              </td>
              <td className="nowrap text-middle">
                {getUserTypeLabel(invitation.userType)}
              </td>
              <td className="nowrap text-middle">
                {formatDate(invitation.invitedOn)}
              </td>
              <td className="nowrap text-middle">{invitation.status}</td>
            </tr>
          ))}
        </Table>
      </div>
      {paging && paging.total > 0 && (
        <ListFooter
          count={invitations.length}
          loadMore={handleLoadMore}
          ready={!loading}
          total={paging.total}
        />
      )}
    </div>
  );
}
