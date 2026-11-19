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

import { Button, ButtonSize, IconDownload } from '@sonarsource/echoes-react';
import * as React from 'react';
import { addGlobalErrorMessage } from '~design-system';
import { translate } from '../../../helpers/l10n';
import { CsvTable, downloadCsv } from '../csv';
import { useIsReport } from '../report';

interface Props {
  fileName: string;
  /** What to export; may load more data, such as every page of a list. */
  getTable: () => CsvTable | Promise<CsvTable>;
  label?: string;
}

/** Saves a panel's data as a CSV file. Left out of the PDF report, where there is nothing to click. */
export default function ExportCsvButton({ fileName, getTable, label }: Readonly<Props>) {
  const isReport = useIsReport();
  const [busy, setBusy] = React.useState(false);

  if (isReport) {
    return null;
  }

  const handleClick = async () => {
    setBusy(true);
    try {
      downloadCsv(fileName, await getTable());
    } catch {
      addGlobalErrorMessage(translate('analytics.export.failed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button
      isDisabled={busy}
      isLoading={busy}
      onClick={handleClick}
      prefix={<IconDownload />}
      size={ButtonSize.Medium}
    >
      {label ?? translate('analytics.export.csv')}
    </Button>
  );
}
