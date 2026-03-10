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
import MsaPopup from './MsaPopUp';

export default function MsaGate({
  children,
  enabled,
}: {
  children: React.ReactNode;
  enabled: boolean;
}) {
  const [open, setOpen] = React.useState(false);

  React.useEffect(() => {
    setOpen(enabled);
  }, [enabled]);

if(open){
  return (
      <MsaPopup
        isOpen={open}
        onClose={() => setOpen(false)}
        message={`<div>
          <h1>Welcome to AutoRABIT</h1>
        </div>
        <div>
          By accessing or using AutoRABIT's systems, you acknowledge and agree that your organization is bound by the terms of AutoRABIT’s
          <a href="https://www.autorabit.com/agreement/"> Master Software Agreement ("MSA").</a>
          If your organization has a separately executed agreement with AutoRABIT governing its use of the services, that agreement will control to the extent of any conflict with the MSA.
          <br/><br/>
          By continuing, you confirm that you are authorized to accept the Master Software Agreement on behalf of your organization.
          <br/><br/>
          Please review the MSA carefully before proceeding.
        </div>`}
        requireCheckbox={true}
        checkboxText="I have read and agree to the MSA and confirm that I am authorized to accept on behalf of my organization."
        primaryButtonText="Accept and Continue"
      />
  );
}
 return <>{children}</>;
}
