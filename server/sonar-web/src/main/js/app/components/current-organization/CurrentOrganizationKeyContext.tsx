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

import { noop } from 'lodash';
import * as React from 'react';
import { useLocation } from 'react-router-dom';

interface CurrentOrganizationKeyContextShape {
  organizationKey?: string;
  setOrganizationKey: (organizationKey?: string) => void;
}

/**
 * Exposes the organization the user is currently looking at to components (such as the
 * global navigation) that render *above* the organization/component route tree and so
 * cannot rely on route params or the component context.
 *
 * The key is resolved from the URL for organization-scoped pages
 * (`/organizations/:organizationKey/...`). For project/component pages
 * (`/dashboard?id=...`, `/project/issues?id=...`, ...) the URL carries the component key
 * rather than the organization, so `ComponentContainer` feeds the resolved organization
 * back through `setOrganizationKey`.
 */
export const CurrentOrganizationKeyContext =
  React.createContext<CurrentOrganizationKeyContextShape>({
    organizationKey: undefined,
    setOrganizationKey: noop,
  });

export function useCurrentOrganizationKey(): CurrentOrganizationKeyContextShape {
  return React.useContext(CurrentOrganizationKeyContext);
}

const ORGANIZATION_PATH_RE = /^\/organizations\/([^/]+)/;

export default function CurrentOrganizationKeyProvider({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const { pathname, search } = useLocation();
  const [organizationKey, setOrganizationKey] = React.useState<string | undefined>();

  React.useEffect(() => {
    const match = ORGANIZATION_PATH_RE.exec(pathname);

    if (match && match[1] !== 'create') {
      setOrganizationKey(match[1]);
      return;
    }

    // On component pages the organization is not in the URL; ComponentContainer owns
    // the value in that case, so we leave it untouched. Anywhere else there is no
    // organization context, so clear any previously detected key.
    if (!new URLSearchParams(search).has('id')) {
      setOrganizationKey(undefined);
    }
  }, [pathname, search]);

  const value = React.useMemo(
    () => ({ organizationKey, setOrganizationKey }),
    [organizationKey],
  );

  return (
    <CurrentOrganizationKeyContext.Provider value={value}>
      {children}
    </CurrentOrganizationKeyContext.Provider>
  );
}
