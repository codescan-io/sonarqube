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
import classNames from 'classnames';
import { ItemCheckbox } from './DropdownMenu';

export interface MultiSelectOptionProps {
  active?: boolean;
  createElementLabel: string;
  custom?: boolean;
  disabled?: boolean;
  element: string;
  onHover: (element: string) => void;
  onSelectChange: (selected: boolean, element: string) => void;
  selected?: boolean;
}

export function MultiSelectOption(props: MultiSelectOptionProps) {
  const { active, createElementLabel, custom, disabled, element, onSelectChange, selected } = props;
  const onHover = () => props.onHover(element);

  return (
    <ItemCheckbox
      checked={Boolean(selected)}
      className={classNames('sw-flex sw-py-2 sw-px-4', { active })}
      disabled={disabled}
      id={element}
      onCheck={onSelectChange}
      onFocus={onHover}
      onPointerEnter={onHover}
    >
      {custom ? (
        <span
          aria-label={`${createElementLabel}: ${element}`}
          className="sw-ml-3"
          title={createElementLabel}
        >
          <span aria-hidden className="sw-mr-1">
            +
          </span>
          {element}
        </span>
      ) : (
        <span className="sw-ml-3">{element}</span>
      )}
    </ItemCheckbox>
  );
}
