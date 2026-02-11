import * as React from 'react';
import { Modal, SafeHTMLInjection, HtmlFormatter, themeColor } from '~design-system';
import { Button, ButtonVariety } from '@sonarsource/echoes-react';
import { FormattedMessage } from 'react-intl';
import { noop } from 'lodash';
import styled from '@emotion/styled';
import { throwGlobalError } from '~sonar-aligned/helpers/error';
import "./MsaPopUp.css"
import { acceptEulaVersion } from '../../../api/eula';

export interface MsaPopupProps {
  message: string;
  isOpen: boolean;
  onClose: () => void;
  requireCheckbox?: boolean;
  checkboxText?: string;
  primaryButtonText?: string;
}

export default function MsaPopUp({
  message,
  isOpen,
  onClose,
  requireCheckbox = false,
  checkboxText,
  primaryButtonText
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
      await acceptEulaVersion();
    } catch (e) {
      console.error("Error accepting the eula version ", e);
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
