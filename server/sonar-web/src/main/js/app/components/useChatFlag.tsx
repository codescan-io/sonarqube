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
// server/sonar-web/src/main/js/app/hooks/useChatFlag.ts
import { useEffect, useState } from 'react';

export function useChatFlag() {
  const [enabled, setEnabled] = useState<boolean | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const res = await fetch(
          'http://localhost:3000/api/settings/values?keys=codescan.chatbot.enabled',
          {
            headers: { Accept: 'application/json' },
          },
        );
        const json = await res.json();
        const raw = json.settings?.[0]?.value ?? 'false';
        if (!cancelled) setEnabled(String(raw).toLowerCase() === 'true');
      } catch {
        if (!cancelled) setEnabled(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  return enabled; // null while loading
}
