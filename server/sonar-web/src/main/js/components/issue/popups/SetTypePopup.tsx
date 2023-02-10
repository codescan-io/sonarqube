/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
import { DropdownOverlay } from '../../../components/controls/Dropdown';
import IssueTypeIcon from '../../../components/icons/IssueTypeIcon';
import { translate } from '../../../helpers/l10n';
import { Issue, IssueType } from '../../../types/types';
import SelectList from '../../common/SelectList';
import SelectListItem from '../../common/SelectListItem';

interface Props {
  issue: Pick<Issue, 'type'>;
  onSelect: (type: IssueType) => void;
}

const TYPES = ['BUG', 'VULNERABILITY', 'CODE_SMELL'];

export default function SetTypePopup({ issue, onSelect }: Props) {
  return (
    <DropdownOverlay>
      <SelectList currentItem={issue.type} items={TYPES} onSelect={onSelect}>
        {TYPES.map((type) => (
          <SelectListItem className="display-flex-center" item={type} key={type}>
            <IssueTypeIcon className="little-spacer-right" query={type} />
            {translate('issue.type', type)}
          </SelectListItem>
        ))}
      </SelectList>
    </DropdownOverlay>
  );
}
