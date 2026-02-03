import * as React from 'react';
import { Modal, SafeHTMLInjection, HtmlFormatter, themeColor } from '~design-system';
import { Button, ButtonVariety } from '@sonarsource/echoes-react';
import { FormattedMessage } from 'react-intl';
import { noop } from 'lodash';
import styled from '@emotion/styled';
import { postJSON } from '../../../helpers/request';
import { throwGlobalError } from '~sonar-aligned/helpers/error';

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
        <MsaContentStyle>
          <HtmlFormatter>
            <SafeHTMLInjection htmlAsString={message} />
          </HtmlFormatter>
        </MsaContentStyle>
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

const MsaContentStyle = styled.div`
  font-family: Overpass, sans-serif;

  h1 {
    font-weight: 300;
    font-size: 1.5rem;
    line-height: 2rem;
    margin: 0 0 0.75rem 0;
  }

  p {
    margin: 0;
    color: ${themeColor('pageContent')};
    line-height: 1.25rem;
  }

  a {
    color: #2563eb;
  }
`;
