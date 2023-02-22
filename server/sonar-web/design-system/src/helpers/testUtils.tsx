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
import { render as rtlRender, RenderOptions } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Options as UserEventsOptions } from '@testing-library/user-event/dist/types/options';
import { InitialEntry } from 'history';
import { identity, kebabCase } from 'lodash';
import React, { PropsWithChildren, ReactNode } from 'react';
import { HelmetProvider } from 'react-helmet-async';
import { IntlProvider } from 'react-intl';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

export function render(
  ui: React.ReactElement,
  options?: RenderOptions,
  userEventOptions?: UserEventsOptions
) {
  return { ...rtlRender(ui, options), user: userEvent.setup(userEventOptions) };
}

type RenderContextOptions = Omit<RenderOptions, 'wrapper'> & {
  initialEntries?: InitialEntry[];
  userEventOptions?: UserEventsOptions;
};

export function renderWithContext(
  ui: React.ReactElement,
  { userEventOptions, ...options }: RenderContextOptions = {}
) {
  return render(ui, { ...options, wrapper: getContextWrapper() }, userEventOptions);
}

type RenderRouterOptions = { additionalRoutes?: ReactNode };

export function renderWithRouter(
  ui: React.ReactElement,
  options: RenderContextOptions & RenderRouterOptions = {}
) {
  const { additionalRoutes, userEventOptions, ...renderOptions } = options;

  function RouterWrapper({ children }: React.PropsWithChildren<{}>) {
    return (
      <HelmetProvider>
        <MemoryRouter>
          <Routes>
            <Route element={children} path="/" />
            {additionalRoutes}
          </Routes>
        </MemoryRouter>
      </HelmetProvider>
    );
  }

  return render(ui, { ...renderOptions, wrapper: RouterWrapper }, userEventOptions);
}

function getContextWrapper() {
  return function ContextWrapper({ children }: React.PropsWithChildren<{}>) {
    return (
      <HelmetProvider>
        <IntlProvider defaultLocale="en" locale="en">
          {children}
        </IntlProvider>
      </HelmetProvider>
    );
  };
}

export function mockComponent(name: string, transformProps: (props: any) => any = identity) {
  function MockedComponent({ ...props }: PropsWithChildren<any>) {
    return React.createElement('mocked-' + kebabCase(name), transformProps(props));
  }

  MockedComponent.displayName = `mocked(${name})`;
  return MockedComponent;
}

export const debounceTimer = jest.fn().mockImplementation((callback, timeout) => {
  let timeoutId: number;
  const debounced = jest.fn((...args) => {
    window.clearTimeout(timeoutId);
    timeoutId = window.setTimeout(() => callback(...args), timeout);
  });
  (debounced as any).cancel = jest.fn(() => {
    window.clearTimeout(timeoutId);
  });
  return debounced;
});

export function flushPromises(usingFakeTime = false): Promise<void> {
  return new Promise((resolve) => {
    if (usingFakeTime) {
      jest.useRealTimers();
    }
    setTimeout(resolve, 0);
    if (usingFakeTime) {
      jest.useFakeTimers();
    }
  });
}
