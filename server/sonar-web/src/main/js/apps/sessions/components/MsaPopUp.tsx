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
import { Modal, SafeHTMLInjection, HtmlFormatter, themeColor } from '~design-system';
import { Button, ButtonVariety } from '@sonarsource/echoes-react';
import { FormattedMessage } from 'react-intl';
import { noop } from 'lodash';
import styled from '@emotion/styled';
import { postJSON } from '../../../helpers/request';
import { throwGlobalError } from '~sonar-aligned/helpers/error';
import "./MsaPopUp.css"

export interface MsaPopupProps {
  message: string;
  isOpen: boolean;
  onClose: () => void;
  requireCheckbox?: boolean;
  checkboxText?: string;
  primaryButtonText?: string;
  endpoint?: string;
  payload?: Record<string, unknown>;
}

export default function MsaPopUp({
  message,
  isOpen,
  onClose,
  requireCheckbox = false,
  checkboxText,
  primaryButtonText,
  endpoint = '/_codescan/eula/accept',
  payload = {},
}: Readonly<MsaPopupProps>) {
  const [checked, setChecked] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);

  React.useEffect(() => {
    if (isOpen) {
      setChecked(false);
      setSubmitting(false);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const isPrimaryDisabled = (requireCheckbox ? !checked : false) || submitting;

  const handlePrimary = async () => {
    if (submitting) return;
    setSubmitting(true);

    try {
      await postJSON(endpoint, payload);
    } catch (e) {
      // postJSON rejects with a fetch Response (from checkStatus)
      if (e instanceof Response) {
        await throwGlobalError(e);
      } else {
        await throwGlobalError(e as any);
      }
    } finally {
      onClose();
    }
  };

  return (
    <Modal onClose={noop} closeOnOverlayClick={false}>
      <Modal.Body>
        <div className="msaPopup-elements-styles">
          <HtmlFormatter>
            <SafeHTMLInjection htmlAsString={message} />
          </HtmlFormatter>
        </div>
        {requireCheckbox && (
          <label className="sw-flex sw-gap-2 sw-items-start sw-mt-4">
            <input
              type="checkbox"
              checked={checked}
              disabled={submitting}
              onChange={(e) => setChecked(e.currentTarget.checked)}
              style={{ marginTop: 3 }}
            />
            <span className="sw-text-sm">
              <strong>{checkboxText}</strong>
            </span>
          </label>
        )}
      </Modal.Body>

      <Modal.Footer
        primaryButton={
          <Button
            onClick={handlePrimary}
            variety={ButtonVariety.Primary}
            isDisabled={isPrimaryDisabled}
          >
              {submitting ? 'Accepting…' : primaryButtonText ?? (
                  <FormattedMessage id="login.us_data_protection_consent.button_primary" />
              )}
          </Button>
        }
        secondaryButton={null}
      />
    </Modal>
  );
}
