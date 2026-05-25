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

import { useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { CreatePRButton, CreatePRIcon, CreatePRText } from './FixDiffStyles';
import { CreatePullRequestModal } from './CreatePullRequestModal';

interface CreatePullRequestButtonProps {
  jobId?: string;
  issueKey?: string;
  pullRequestAlreadyCreated?: boolean;
}

export function CreatePullRequestButton({
  jobId,
  issueKey,
  pullRequestAlreadyCreated,
}: Readonly<CreatePullRequestButtonProps>) {
  const queryClient = useQueryClient();
  const [isActive, setIsActive] = React.useState(false);
  const [modalOpen, setModalOpen] = React.useState(false);

  const handleModalClose = React.useCallback(() => {
    setModalOpen(false);
    if (jobId && issueKey) {
      queryClient.invalidateQueries({ queryKey: ['codefix-get-pr-status', jobId] });
      queryClient.invalidateQueries({ queryKey: ['codefix-fixed-file', issueKey] });
      queryClient.invalidateQueries({ queryKey: ['codefix-status', issueKey] });
    }
  }, [jobId, issueKey, queryClient]);

  const disabled = !jobId || !issueKey || Boolean(pullRequestAlreadyCreated);

  return (
    <>
      <CreatePRButton
        type="button"
        onClick={() => setModalOpen(true)}
        disabled={disabled}
        $active={isActive || Boolean(pullRequestAlreadyCreated)}
      >
        <CreatePRIcon>
          <img src="/images/pull-request-icon.svg" className="" alt="pull-request-icon" />
        </CreatePRIcon>
        <CreatePRText>Create Pull Request</CreatePRText>
      </CreatePRButton>
      {modalOpen && jobId && issueKey && (
        <CreatePullRequestModal
          issueKey={issueKey}
          jobId={jobId}
          onClose={handleModalClose}
          onSuccess={() => {
            setIsActive(true);
          }}
        />
      )}
    </>
  );
}

