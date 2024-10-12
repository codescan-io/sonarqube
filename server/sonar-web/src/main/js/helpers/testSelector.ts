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

import {
  BoundFunction,
  GetByBoundAttribute,
  GetByRole,
  GetByText,
  screen,
  waitForOptions,
  within,
} from '@testing-library/react';

function maybeScreen(container?: HTMLElement) {
  return container ? within(container) : screen;
}

export interface ReactTestingQuery {
  find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ): Promise<T>;
  findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ): Promise<T[]>;
  get<T extends HTMLElement = HTMLElement>(container?: HTMLElement): T;
  getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement): T[];
  query<T extends HTMLElement = HTMLElement>(container?: HTMLElement): T | null;
  queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement): T[] | null;

  byText(...args: Parameters<BoundFunction<GetByText>>): ReactTestingQuery;
  byRole(...args: Parameters<BoundFunction<GetByRole>>): ReactTestingQuery;
  byPlaceholderText(...args: Parameters<BoundFunction<GetByBoundAttribute>>): ReactTestingQuery;
  byLabelText(...args: Parameters<BoundFunction<GetByText>>): ReactTestingQuery;
  byTestId(...args: Parameters<BoundFunction<GetByBoundAttribute>>): ReactTestingQuery;
  byDisplayValue(...args: Parameters<BoundFunction<GetByBoundAttribute>>): ReactTestingQuery;
}

abstract class ChainingQuery implements ReactTestingQuery {
  abstract find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ): Promise<T>;

  abstract findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ): Promise<T[]>;

  abstract get<T extends HTMLElement = HTMLElement>(container?: HTMLElement): T;

  abstract getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement): T[];

  abstract query<T extends HTMLElement = HTMLElement>(container?: HTMLElement): T | null;

  abstract queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement): T[] | null;

  byText(...args: Parameters<BoundFunction<GetByText>>): ReactTestingQuery {
    return new ChainDispatch(this, new DispatchByText(args));
  }

  byRole(...args: Parameters<BoundFunction<GetByRole>>): ReactTestingQuery {
    return new ChainDispatch(this, new DispatchByRole(args));
  }

  byPlaceholderText(...args: Parameters<BoundFunction<GetByBoundAttribute>>): ReactTestingQuery {
    return new ChainDispatch(this, new DispatchByPlaceholderText(args));
  }

  byLabelText(...args: Parameters<BoundFunction<GetByText>>): ReactTestingQuery {
    return new ChainDispatch(this, new DispatchByLabelText(args));
  }

  byTestId(...args: Parameters<BoundFunction<GetByBoundAttribute>>): ReactTestingQuery {
    return new ChainDispatch(this, new DispatchByTestId(args));
  }

  byDisplayValue(...args: Parameters<BoundFunction<GetByBoundAttribute>>): ReactTestingQuery {
    return new ChainDispatch(this, new DispatchByDisplayValue(args));
  }
}

class ChainDispatch extends ChainingQuery {
  insideQuery: ReactTestingQuery;
  elementQuery: ReactTestingQuery;

  constructor(insideQuery: ReactTestingQuery, elementQuery: ReactTestingQuery) {
    super();
    this.insideQuery = insideQuery;
    this.elementQuery = elementQuery;
  }

  async find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return this.elementQuery.get<T>(await this.insideQuery.find(container, waitForOptions));
  }

  async findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return this.elementQuery.getAll<T>(await this.insideQuery.find(container, waitForOptions));
  }

  get<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return this.elementQuery.get<T>(this.insideQuery.get(container));
  }

  getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return this.elementQuery.getAll<T>(this.insideQuery.get(container));
  }

  query<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return this.elementQuery.query<T>(this.insideQuery.get(container));
  }

  queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return this.elementQuery.queryAll<T>(this.insideQuery.get(container));
  }
}

class DispatchByText extends ChainingQuery {
  readonly args: Parameters<BoundFunction<GetByText>>;

  constructor(args: Parameters<BoundFunction<GetByText>>) {
    super();
    this.args = args;
  }

  find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findByText<T>(...this.args, waitForOptions);
  }

  findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findAllByText<T>(...this.args, waitForOptions);
  }

  get<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getByText<T>(...this.args);
  }

  getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getAllByText<T>(...this.args);
  }

  query<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryByText<T>(...this.args);
  }

  queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryAllByText<T>(...this.args);
  }
}

class DispatchByLabelText extends ChainingQuery {
  readonly args: Parameters<BoundFunction<GetByText>>;

  constructor(args: Parameters<BoundFunction<GetByText>>) {
    super();
    this.args = args;
  }

  find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findByLabelText<T>(...this.args, waitForOptions);
  }

  findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findAllByLabelText<T>(...this.args, waitForOptions);
  }

  get<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getByLabelText<T>(...this.args);
  }

  getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getAllByLabelText<T>(...this.args);
  }

  query<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryByLabelText<T>(...this.args);
  }

  queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryAllByLabelText<T>(...this.args);
  }
}

class DispatchByRole extends ChainingQuery {
  readonly args: Parameters<BoundFunction<GetByRole>>;

  constructor(args: Parameters<BoundFunction<GetByRole>>) {
    super();
    this.args = args;
  }

  find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findByRole<T>(...this.args, waitForOptions);
  }

  findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findAllByRole<T>(...this.args, waitForOptions);
  }

  get<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getByRole<T>(...this.args);
  }

  getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getAllByRole<T>(...this.args);
  }

  query<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryByRole<T>(...this.args);
  }

  queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryAllByRole<T>(...this.args);
  }
}

class DispatchByTestId extends ChainingQuery {
  readonly args: Parameters<BoundFunction<GetByBoundAttribute>>;

  constructor(args: Parameters<BoundFunction<GetByBoundAttribute>>) {
    super();
    this.args = args;
  }

  find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findByTestId<T>(...this.args, waitForOptions);
  }

  findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findAllByTestId<T>(...this.args, waitForOptions);
  }

  get<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getByTestId<T>(...this.args);
  }

  getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getAllByTestId<T>(...this.args);
  }

  query<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryByTestId<T>(...this.args);
  }

  queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryAllByTestId<T>(...this.args);
  }
}

class DispatchByDisplayValue extends ChainingQuery {
  readonly args: Parameters<BoundFunction<GetByBoundAttribute>>;

  constructor(args: Parameters<BoundFunction<GetByBoundAttribute>>) {
    super();
    this.args = args;
  }

  find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findByDisplayValue<T>(...this.args, waitForOptions);
  }

  findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findAllByDisplayValue<T>(...this.args, waitForOptions);
  }

  get<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getByDisplayValue<T>(...this.args);
  }

  getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getAllByDisplayValue<T>(...this.args);
  }

  query<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryByDisplayValue<T>(...this.args);
  }

  queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryAllByDisplayValue<T>(...this.args);
  }
}

class DispatchByPlaceholderText extends ChainingQuery {
  readonly args: Parameters<BoundFunction<GetByBoundAttribute>>;

  constructor(args: Parameters<BoundFunction<GetByBoundAttribute>>) {
    super();
    this.args = args;
  }

  find<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findByPlaceholderText<T>(...this.args, waitForOptions);
  }

  findAll<T extends HTMLElement = HTMLElement>(
    container?: HTMLElement,
    waitForOptions?: waitForOptions
  ) {
    return maybeScreen(container).findAllByPlaceholderText<T>(...this.args, waitForOptions);
  }

  get<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getByPlaceholderText<T>(...this.args);
  }

  getAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).getAllByPlaceholderText<T>(...this.args);
  }

  query<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryByPlaceholderText<T>(...this.args);
  }

  queryAll<T extends HTMLElement = HTMLElement>(container?: HTMLElement) {
    return maybeScreen(container).queryAllByPlaceholderText<T>(...this.args);
  }
}

export function byText(...args: Parameters<BoundFunction<GetByText>>) {
  return new DispatchByText(args);
}

export function byRole(...args: Parameters<BoundFunction<GetByRole>>) {
  return new DispatchByRole(args);
}

export function byPlaceholderText(...args: Parameters<BoundFunction<GetByBoundAttribute>>) {
  return new DispatchByPlaceholderText(args);
}

export function byLabelText(...args: Parameters<BoundFunction<GetByText>>) {
  return new DispatchByLabelText(args);
}

export function byTestId(...args: Parameters<BoundFunction<GetByBoundAttribute>>) {
  return new DispatchByTestId(args);
}

export function byDisplayValue(...args: Parameters<BoundFunction<GetByBoundAttribute>>) {
  return new DispatchByDisplayValue(args);
}
