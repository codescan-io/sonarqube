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
import { getPendingInvitations, PageResponse, PendingInvitation } from '../../api/organizations';
import { Organization } from '../../types/types';
import './MembersList.css';

interface Props {
  organization: Organization;
}

const PAGE_SIZE = 50;
const AVATAR_SIZE = 36;

// The backend now nests pagination metadata under `page` instead of returning it as flat fields
// (CD-8185). Guard against a missing/malformed envelope the same way ProjectPage.js does for
// the analysis queue response.
function getPageData(response?: PageResponse<PendingInvitation>) {
  if (!response || !response.page || !response.content) {
    return undefined;
  }
  return {
    items: response.content,
    number: response.page.number ?? 0,
    totalElements: response.page.totalElements ?? 0,
  };
}

export default function PendingInvitations({ organization }: Props) {
  const [invitations, setInvitations] = React.useState<PendingInvitation[]>([]);
  const [totalElements, setTotalElements] = React.useState<number>(0);
  const [currentPage, setCurrentPage] = React.useState<number>(0);
  const [loading, setLoading] = React.useState<boolean>(false);
  const [loaded, setLoaded] = React.useState<boolean>(false);

  const fetchInvitations = (page?: number) => {
    setLoading(true);
    getPendingInvitations({
      organizationKee: organization.kee,
      p: page || 1,
      ps: PAGE_SIZE,
    }).then(
      (response) => {
        setLoading(false);
        setLoaded(true);
        const pageData = getPageData(response);
        if (!pageData) return;
        if (page && page > 1) {
          setInvitations((prev) => [...prev, ...pageData.items]);
        } else {
          setInvitations(pageData.items);
        }
        setTotalElements(pageData.totalElements);
        setCurrentPage(pageData.number);
      },
      () => {
        setLoading(false);
        setLoaded(true);
      },
    );
  };

  React.useEffect(() => {
    if (organization.actions && organization.actions.admin) {
      fetchInvitations();
    }
  }, []);

  const handleLoadMore = () => {
    fetchInvitations(currentPage + 2); // currentPage is 0-indexed, API p is 1-indexed
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

  if (!organization.actions?.admin || !loaded) {
    return null;
  }

  const hasInvitations = invitations && invitations.length > 0;

  return (
    <div className="sw-mt-16 sw-ml-16 sw-mr-8">
      <h2 className="page-title">Pending Invitations</h2>
      {!hasInvitations ? (
        <div className="note sw-mt-4">
          No pending invitations. Invite new members to collaborate.
        </div>
      ) : (
        <>
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
                      name={invitation.email ? getNameFromEmail(invitation.email) : '?'}
                      size={AVATAR_SIZE}
                    />
                  </td>
                  <td className="nowrap text-middle" style={{ width: '40%' }}>
                    <strong>{invitation.email ? getNameFromEmail(invitation.email) : ''}</strong>
                    <span className="note sw-ml-2">{invitation.email || ''}</span>
                  </td>
                  <td className="nowrap text-middle">
                    {getUserTypeLabel(invitation.userType)}
                  </td>
                  <td className="nowrap text-middle">
                    {invitation.invitedOn ? formatDate(invitation.invitedOn) : ''}
                  </td>
                  <td className="nowrap text-middle">Invited</td>
                </tr>
              ))}
            </Table>
          </div>
          {totalElements > 0 && (
            <ListFooter
              count={invitations.length}
              loadMore={handleLoadMore}
              ready={!loading}
              total={totalElements}
            />
          )}
        </>
      )}
    </div>
  );
}
