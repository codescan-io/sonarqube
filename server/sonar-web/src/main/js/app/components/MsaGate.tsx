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
          message={`<div><div style="
        font-family: Overpass, sans-serif;
        font-weight: 300;
        font-size: 24px;
        line-height: 32px;
        letter-spacing: 0%;
        vertical-align: middle;
        margin: 0 0 12px 0;
      ">
        <h4>Welcome to Codescan</h4>
      </div>
      <div style="
        font-family: Overpass, sans-serif;
      "> I confirm that I am authorized to accept the <a href="https://www.autorabit.com/agreement/"> Master Software Agreement</a> on behalf of my organization.</div>`}
          requireCheckbox={true}
          checkboxText="I have read and agree the AutoRABIT Master Software Agreement"
          primaryButtonText="Accept and Continue"
        />
      </>
    );
  }

  return <>{children}</>;
}
