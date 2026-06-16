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

import { useState } from 'react';
import { useIntl } from 'react-intl';
import {
  ButtonPrimary,
  ButtonSecondary,
  DatePicker,
  FormField,
  InputTextArea,
  ItemDivider,
  LightPrimary,
  Note,
  PageContentFontWrapper,
  SelectionCard,
  Spinner,
} from '~design-system';
import { translate } from '../../../helpers/l10n';
import { IssueActions, IssueTransition } from '../../../types/issues';
import { Issue } from '../../../types/types';
import {
  isTransitionDeprecated,
  isTransitionHidden,
  issueSupportsExceptionExpiryPicker,
  transitionRequiresComment,
  transitionRequiresMandatoryComment,
} from '../helpers';
import { IssueTransitionItem } from './IssueTransitionItem';
import './IssueTransitionOverlay.css';

export type Props = {
  issue: Pick<Issue, 'transitions' | 'actions' | 'status' | 'type'>;
  loading?: boolean;
  onClose: () => void;
  onSetTransition: (
    transition: IssueTransition,
    exceptionReason?: string,
    comment?: string,
    issueResolutionExpiryDate?: string,
  ) => void;
};

export function IssueTransitionOverlay(props: Readonly<Props>) {
  const { issue, onClose, onSetTransition, loading } = props;
  const intl = useIntl();

  const [comment, setComment] = useState('');
  const [selectedTransition, setSelectedTransition] = useState<IssueTransition>();
  const [exceptionExpiryDate, setExceptionExpiryDate] = useState<Date | undefined>();

  const hasCommentAction = issue.actions.includes(IssueActions.Comment);
  const showExceptionExpiry = issueSupportsExceptionExpiryPicker(issue);
  const showExceptionExpiryControls =
    selectedTransition === IssueTransition.Exception && showExceptionExpiry;

  function selectTransition(transition: IssueTransition) {
    if (!transitionRequiresComment(transition) || !hasCommentAction) {
      onSetTransition(transition, undefined, undefined, buildExpiryPayload(transition));
    } else {
      setSelectedTransition(transition);
      setExceptionExpiryDate(undefined);
    }
  }

  function buildExpiryPayload(transition: IssueTransition): string | undefined {
    if (transition !== IssueTransition.Exception || !showExceptionExpiry) {
      return undefined;
    }
    if (exceptionExpiryDate) {
      const yyyy = exceptionExpiryDate.getFullYear();
      const mm = String(exceptionExpiryDate.getMonth() + 1).padStart(2, '0');
      const dd = String(exceptionExpiryDate.getDate()).padStart(2, '0');
      return `${yyyy}-${mm}-${dd}`;
    }
    return undefined;
  }

  function handleResolve() {
    if (selectedTransition) {
      if (transitionRequiresMandatoryComment(selectedTransition) && !comment.trim()) {
            return;
      }
      onSetTransition(
        selectedTransition,
        selectedTransition === IssueTransition.Exception ? comment : undefined,
        selectedTransition === IssueTransition.Exception ? undefined : comment,
        buildExpiryPayload(selectedTransition),
      );
    }
  }

  // Filter out hidden transitions and separate deprecated transitions in a different list
  const filteredTransitions = issue.transitions.filter(
    (transition) => !isTransitionHidden(transition),
  );
  const filteredTransitionsRecommended = filteredTransitions.filter(
    (t) => !isTransitionDeprecated(t),
  );
  const filteredTransitionsDeprecated = filteredTransitions.filter(isTransitionDeprecated);

  return (
    <ul className="sw-flex sw-flex-col issues-status-panel">
      {filteredTransitionsRecommended.map((transition) => (
        <IssueTransitionItem
          key={transition}
          transition={transition}
          selected={selectedTransition === transition}
          onSelectTransition={selectTransition}
        />
      ))}
      {filteredTransitionsRecommended.length > 0 && filteredTransitionsDeprecated.length > 0 && (
        <ItemDivider />
      )}
      {filteredTransitionsDeprecated.map((transition) => (
        <IssueTransitionItem
          key={transition}
          transition={transition}
          selected={selectedTransition === transition}
          onSelectTransition={selectTransition}
        />
      ))}

      {selectedTransition && (
        <>
          <ItemDivider />
          <div className="sw-mx-4 sw-mt-2">
            {selectedTransition === IssueTransition.Exception ? (
              <SelectionCard
                className="sw-mb-2"
                selected
                vertical
                title={translate('issue.transition.exception')}
                onClick={() => {
                  /* noop: keeps radio + selected border; inputs handle their own focus */
                }}
              >
                <Note className="sw-mt-1 sw-mr-12">
                  {translate('issue.transition.exception.description')}
                </Note>
                {showExceptionExpiryControls && (
                  <div className="sw-mt-10 sw-flex sw-flex-wrap sw-items-center sw-gap-3">
                    <DatePicker
                      clearButtonLabel={translate('clear')}
                      minDate={(() => {
                        const d = new Date();
                        d.setHours(0, 0, 0, 0);
                        return d;
                      })()}
                      maxDate={new Date(2050, 11, 31)}
                      name="issueExceptionExpiry"
                      onChange={(d?: Date) => setExceptionExpiryDate(d)}
                      placeholder={translate('issue.exception.expiry.no_expiry_placeholder')}
                      value={exceptionExpiryDate}
                    />
                  </div>
                )}
                <FormField
                  className={showExceptionExpiryControls ? 'sw-mt-6' : 'sw-mt-10'}
                  htmlFor="issue-exception-reason-textarea"
                  label={
                    <LightPrimary className="sw-typo-semibold">
                      {`${translate('issue.transition.exception.reason')} *`}
                    </LightPrimary>
                  }
                >
                  <InputTextArea
                    autoFocus
                    className="sw-mb-2 sw-resize-y"
                    id="issue-exception-reason-textarea"
                    onChange={(event) => setComment(event.currentTarget.value)}
                    placeholder={translate(
                      'issue.transition.comment.placeholder',
                      selectedTransition,
                    )}
                    rows={4}
                    size="full"
                    value={comment}
                  />
                </FormField>
              </SelectionCard>
            ) : (
              <>
                <PageContentFontWrapper className="sw-font-semibold">
                  {intl.formatMessage({ id: 'issue.transition.comment' })}
                </PageContentFontWrapper>
                <InputTextArea
                  autoFocus
                  onChange={(event) => setComment(event.currentTarget.value)}
                  placeholder={translate(
                    'issue.transition.comment.placeholder',
                    selectedTransition ?? '',
                  )}
                  rows={5}
                  value={comment}
                  size="large"
                  className="sw-mt-2"
                />
              </>
            )}
            <Spinner loading={loading} className="sw-float-right sw-m-2">
              <div className="sw-mt-2 sw-flex sw-gap-3 sw-justify-end">
                <ButtonPrimary
                  onClick={handleResolve}
                  disabled={transitionRequiresMandatoryComment(selectedTransition) && !comment.trim()}
                >
                  {transitionRequiresMandatoryComment(selectedTransition)
                    ? translate('issue.change_status')
                    : translate('resolve')}
                </ButtonPrimary>
                <ButtonSecondary onClick={onClose}>{translate('cancel')}</ButtonSecondary>
              </div>
            </Spinner>
          </div>
        </>
      )}

      {!selectedTransition && loading && (
        <div className="sw-flex sw-justify-center sw-m-2">
          <Spinner loading className="sw-float-right sw-2" />
        </div>
      )}
    </ul>
  );
}
