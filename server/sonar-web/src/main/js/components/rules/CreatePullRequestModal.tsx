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

import { Button, ButtonVariety } from '@sonarsource/echoes-react';
import { useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import {
  FlagMessage,
  FormField,
  InputField,
  InputTextArea,
  Modal,
  Spinner,
} from '~design-system';
import {
  createCodefixPr,
  getCodefixCreatePrDraft,
  type CodefixCreatePrDraft,
} from '../../api/ai-codefix';
import { translate } from '../../helpers/l10n';
import { CodefixPrStatusBanner, useCodefixPrStatusQuery } from './PrStatusNotification';

interface CreatePrError {
  message: string;
  ctaUrl?: string;
}

function parseCreatePrErrorMessage(err: unknown): Promise<CreatePrError> {
  if (!(err instanceof Response)) {
      return Promise.resolve({
        message: translate('issues.code_fix.create_pr_modal.submit_error'),
      });
    }
    return err
      .json()
      .then((data: { error?: string; message?: string; ctaUrl?: string; }) => ({
        message:
          data?.error ||
          data?.message ||
          translate('issues.code_fix.create_pr_modal.submit_error'),
        ctaUrl: data?.ctaUrl,
      }))
      .catch(() => ({
        message: translate('issues.code_fix.create_pr_modal.submit_error'),
      }));
}

interface CreatePullRequestModalProps {
  jobId: string;
  issueKey: string;
  onClose: () => void;
  onSuccess: () => void;
}

export function CreatePullRequestModal({
  jobId,
  issueKey,
  onClose,
  onSuccess,
}: Readonly<CreatePullRequestModalProps>) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = React.useState<CodefixCreatePrDraft | null>(null);
  const [loadError, setLoadError] = React.useState(false);
  const [loadingDraft, setLoadingDraft] = React.useState(true);
  const [submitting, setSubmitting] = React.useState(false);

  const [sourceBranchSuffix, setSourceBranchSuffix] = React.useState('');
  const [prTitle, setPrTitle] = React.useState('');
  const [commitMessage, setCommitMessage] = React.useState('');
  const [description, setDescription] = React.useState('');
//   const [submitError, setSubmitError] = React.useState('');
  const [submitError, setSubmitError] = React.useState<CreatePrError | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    setLoadingDraft(true);
    setLoadError(false);
    setSubmitError(null);
    getCodefixCreatePrDraft(jobId, issueKey)
      .then((d) => {
        if (!cancelled) {
          setDraft(d);
          setSourceBranchSuffix(d.sourceBranchSuffix);
          setPrTitle(d.pullRequestTitle);
          setCommitMessage(d.commitMessage);
          setDescription(d.pullRequestDescription);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError(true);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingDraft(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [jobId]);

  const handleSubmit = React.useCallback(() => {
    setSubmitError(null);
    setSubmitting(true);
    createCodefixPr(jobId, issueKey, {
      sourceBranchSuffix,
      pullRequestTitle: prTitle,
      commitMessage,
      pullRequestDescription: description,
    })
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ['codefix-fixed-file', issueKey] });
        queryClient.invalidateQueries({ queryKey: ['codefix-status', issueKey] });
        queryClient.invalidateQueries({ queryKey: ['codefix-get-pr-status', jobId] });
        onSuccess();
        onClose();
      })
      .catch((err: unknown) => {
        void parseCreatePrErrorMessage(err).then(setSubmitError);
      })
      .finally(() => {
        setSubmitting(false);
      });
  }, [
    jobId,
    issueKey,
    sourceBranchSuffix,
    prTitle,
    commitMessage,
    description,
    onClose,
    onSuccess,
    queryClient,
  ]);

  // After a failed submit, clear the error when the user edits any field so they can retry.
  React.useEffect(() => {
    setSubmitError(null);
  }, [sourceBranchSuffix, prTitle, commitMessage, description]);

  const canSubmit = !loadingDraft && !loadError && draft !== null && !submitting;
  const primaryDisabled = !canSubmit || Boolean(submitError);

  const prStatusQuery = useCodefixPrStatusQuery(jobId, issueKey);
  const prStatusType = prStatusQuery.data?.type?.toLowerCase();
  const prStatusMessage = prStatusQuery.data?.message;

  return (
    <Modal
      isLarge
      headerTitle={translate('issues.code_fix.create_pr_modal.title')}
      loading={submitting}
      onClose={onClose}
      secondaryButtonLabel={translate('cancel')}
      body={
        loadingDraft ? (
          <div className="sw-flex sw-justify-center sw-py-6">
            <Spinner ariaLabel={translate('code_viewer.loading')} />
          </div>
        ) : loadError ? (
          <FlagMessage variant="warning">{translate('issues.code_fix.create_pr_modal.load_error')}</FlagMessage>
        ) : (
          <div className="sw-flex sw-flex-col">

            {submitError && (
              <div className="sw-flex sw-flex-col sw-gap-2">
                <CodefixPrStatusBanner
                  jobId={jobId}
                  prStatusMessage={prStatusMessage}
                  prStatusType={prStatusType}
                />
              </div>
            )}

          {submitError && (
              <FlagMessage variant="error">
                <div className="sw-flex sw-flex-col sw-gap-2">
                  <span>{submitError.message}</span>

                  {submitError.ctaUrl && (
                    <a
                      href={submitError.ctaUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="sw-text-blue-500 sw-underline hover:sw-text-blue-600"
                    >
                      Update GitHub App permissions
                    </a>
                  )}
                </div>
              </FlagMessage>
            )}
            <FormField htmlFor="codefix-pr-branch" label={translate('issues.code_fix.create_pr_modal.branch')}>
              <div className="sw-flex sw-flex-wrap sw-items-stretch sw-gap-2 sw-w-full">
                <span
                  className="sw-inline-flex sw-items-center sw-px-3 sw-py-2 sw-rounded-2 sw-border sw-whitespace-nowrap"
                  style={{
                    borderColor: 'var(--color-azure-82, #C5CDDF)',
                    background: 'var(--color-white-solid, #FFF)',
                    color: 'var(--color-azure-48, #637192)',
                    fontSize: 14,
                  }}
                >
                  {draft?.branchNamePrefix}
                </span>
                <div
                  className="sw-flex sw-items-center sw-gap-2 sw-px-3"
                  style={{
                    border: '1px solid var(--echoes-color-border-bold, #C5CDDF)',
                    borderRadius: 'var(--echoes-border-radius-400, 8px)',
                    background: 'var(--echoes-color-background-default, #FFF)',
                    color: 'var(--echoes-color-text-default, #3E4357)',
                    width: 150,
                    height: 32,
                    fontSize: 14,
                  }}
                >
                  <img src="/images/branch-icon.svg" alt="" height={16} width={16} />
                  <InputField
                    className="sw-flex-1"
                    id="codefix-pr-branch"
                    onChange={(e) => setSourceBranchSuffix(e.currentTarget.value)}
                    size="full"
                    type="text"
                    maxLength={20}
                    value={sourceBranchSuffix}
                    style={{
                      border: 'none',
                      outline: 'none',
                      padding: 0,
                      width: '100%',
                      height: '100%',
                      background: 'transparent',
                    }}
                  />
                </div>
              </div>
            </FormField>
            <FormField htmlFor="codefix-pr-title" label={translate('issues.code_fix.create_pr_modal.pr_title')}>
              <InputField
                id="codefix-pr-title"
                onChange={(e) => setPrTitle(e.currentTarget.value)}
                size="full"
                type="text"
                value={prTitle}
              />
            </FormField>
            <FormField
              htmlFor="codefix-pr-commit"
              label={translate('issues.code_fix.create_pr_modal.commit_message')}
            >
              <InputField
                id="codefix-pr-commit"
                onChange={(e) => setCommitMessage(e.currentTarget.value)}
                size="full"
                type="text"
                value={commitMessage}
              />
            </FormField>
            <FormField
              htmlFor="codefix-pr-desc"
              label={translate('issues.code_fix.create_pr_modal.description')}
            >
              <InputTextArea
                className="sw-resize-y"
                id="codefix-pr-desc"
                onChange={(e) => setDescription(e.currentTarget.value)}
                rows={10}
                size="full"
                value={description}
              />
            </FormField>
          </div>
        )
      }
      primaryButton={
        <Button
          isDisabled={primaryDisabled}
          onClick={handleSubmit}
          variety={ButtonVariety.Primary}
        >
          {translate('issues.code_fix.create_pr_modal.title')}
        </Button>
      }
    />
  );
}
