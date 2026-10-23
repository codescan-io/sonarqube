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

import styled from '@emotion/styled';
import {
  ButtonIcon,
  ButtonVariety,
  IconArrowLeft,
  IconArrowRight,
  Text,
} from '@sonarsource/echoes-react';
import { ToggleButton } from '~design-system';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { useIsReport } from '../report';

export const PAGE_SIZES = [10, 25, 50] as const;

interface Props {
  onChange: (page: number, pageSize: number) => void;
  /** 1-based. */
  page: number;
  pageSize: number;
  total: number;
}

/**
 * "11-20 of 2,345" with previous and next buttons, numbered pages around the current one, and a page size choice.
 * Only one page of rows is rendered at a time, however many projects the organization has.
 */
export default function Pagination({ onChange, page, pageSize, total }: Readonly<Props>) {
  const isReport = useIsReport();
  const pages = Math.max(1, Math.ceil(total / pageSize));
  if (isReport || total <= PAGE_SIZES[0]) {
    return null;
  }
  const first = (page - 1) * pageSize + 1;
  const last = Math.min(total, page * pageSize);
  const integer = (value: number) => new Intl.NumberFormat().format(value);

  return (
    <nav
      aria-label={translate('analytics.pagination.label')}
      className="sw-flex sw-flex-wrap sw-items-center sw-justify-between sw-gap-4 sw-mt-4"
    >
      <Text isSubdued>
        {translateWithParameters(
          'analytics.pagination.range',
          integer(first),
          integer(last),
          integer(total),
        )}
      </Text>
      <ul className="sw-flex sw-items-center sw-gap-1">
        <li>
          <ButtonIcon
            Icon={IconArrowLeft}
            ariaLabel={translate('analytics.pagination.previous')}
            isDisabled={page <= 1}
            onClick={() => onChange(page - 1, pageSize)}
            variety={ButtonVariety.DefaultGhost}
          />
        </li>
        {pageNumbers(page, pages).map((number, i) =>
          number === undefined ? (
            // eslint-disable-next-line react/no-array-index-key
            <li aria-hidden className="sw-px-1" key={`gap-${i}`}>
              …
            </li>
          ) : (
            <li key={number}>
              <PageButton
                aria-current={number === page ? 'page' : undefined}
                aria-label={translateWithParameters('analytics.pagination.page', number)}
                onClick={() => onChange(number, pageSize)}
                type="button"
              >
                {number}
              </PageButton>
            </li>
          ),
        )}
        <li>
          <ButtonIcon
            Icon={IconArrowRight}
            ariaLabel={translate('analytics.pagination.next')}
            isDisabled={page >= pages}
            onClick={() => onChange(page + 1, pageSize)}
            variety={ButtonVariety.DefaultGhost}
          />
        </li>
      </ul>
      <ToggleButton
        label={translate('analytics.pagination.page_size')}
        onChange={(size) => onChange(1, size as number)}
        options={PAGE_SIZES.map((size) => ({ label: String(size), value: size }))}
        value={pageSize}
      />
    </nav>
  );
}

/** The first and last page, and the pages around the current one; undefined marks a gap. */
export function pageNumbers(page: number, pages: number): Array<number | undefined> {
  const shown = new Set([1, pages, page - 1, page, page + 1].filter((n) => n >= 1 && n <= pages));
  const sorted = [...shown].sort((a, b) => a - b);
  const result: Array<number | undefined> = [];
  sorted.forEach((n, i) => {
    if (i > 0 && n - sorted[i - 1] > 1) {
      result.push(undefined);
    }
    result.push(n);
  });
  return result;
}

const PageButton = styled.button`
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: none;
  color: var(--echoes-color-text-default);
  font: var(--echoes-typography-text-default-regular);
  cursor: pointer;

  &:hover {
    background-color: var(
      --echoes-color-background-neutral-weak-hover,
      var(--echoes-color-border-weaker)
    );
  }

  &[aria-current='page'] {
    border-color: var(--echoes-color-border-accent);
    background-color: var(--echoes-color-background-accent-weak-default);
    color: var(--echoes-color-text-accent);
    font: var(--echoes-typography-text-default-semi-bold);
  }
`;
