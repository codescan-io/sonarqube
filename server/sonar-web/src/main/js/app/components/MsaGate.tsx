import * as React from 'react';
import DataAccessConsent from '../../apps/sessions/components/DataAccessConsent';

export default function MsaGate({
  children,
  enabled,
}: {
  children: React.ReactNode;
  enabled: boolean;
}) {
  const [show, setShow] = React.useState(false);

  React.useEffect(() => {
    setShow(enabled);
  }, [enabled]);

  if (show) {
    return (
      <>
        {children}
        <DataAccessConsent
          disableSessionStorage={true}
          message={`I confirm that I am authorized to accept the Master Software Agreement on behalf of my organization.`}
          requireCheckbox={true}
          checkboxText="I confirm that I am authorized to accept the Master Software Agreement on behalf of my organization."
          primaryButtonText="Accept and Continue"
        />
      </>
    );
  }

  return <>{children}</>;
}
