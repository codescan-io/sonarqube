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
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserEvent } from '@testing-library/user-event/dist/types/setup/setup';
import { times } from 'lodash';
import { act } from 'react-dom/test-utils';
import { byRole, byText } from 'testing-library-selector';
import ComponentsServiceMock from '../../../api/mocks/ComponentsServiceMock';
import IssuesServiceMock from '../../../api/mocks/IssuesServiceMock';
import { isDiffMetric } from '../../../helpers/measures';
import { mockComponent } from '../../../helpers/mocks/component';
import { mockMeasure } from '../../../helpers/testMocks';
import { renderAppWithComponentContext } from '../../../helpers/testReactTestingUtils';
import { ComponentQualifier } from '../../../types/component';
import { MetricKey } from '../../../types/metrics';
import { Component } from '../../../types/types';
import routes from '../routes';

jest.mock('../../../api/components');
jest.mock('../../../api/issues');
// The following 2 mocks are needed, because IssuesServiceMock mocks more than it should.
// This should be removed once IssuesServiceMock is cleaned up.
jest.mock('../../../api/rules');
jest.mock('../../../api/users');

jest.mock('../../../components/intl/DateFromNow');

jest.mock('../../../components/SourceViewer/helpers/lines', () => {
  const lines = jest.requireActual('../../../components/SourceViewer/helpers/lines');
  return {
    ...lines,
    LINES_TO_LOAD: 20,
  };
});

const DEFAULT_LINES_LOADED = 19;
const originalScrollTo = window.scrollTo;

const issuesHandler = new IssuesServiceMock();
const componentsHandler = new ComponentsServiceMock();

beforeAll(() => {
  Object.defineProperty(window, 'scrollTo', {
    writable: true,
    value: () => {
      /* noop */
    },
  });
});

afterAll(() => {
  Object.defineProperty(window, 'scrollTo', {
    writable: true,
    value: originalScrollTo,
  });
});

beforeEach(() => {
  issuesHandler.reset();
  componentsHandler.reset();
});

it('should allow navigating through the tree', async () => {
  const ui = getPageObject(userEvent.setup());
  renderCode();
  await ui.appLoaded();

  // Navigate by clicking on an element.
  await ui.clickOnChildComponent(/folderA$/);
  expect(await ui.childComponent(/out\.tsx/).find()).toBeInTheDocument();

  // Navigate back using the breadcrumb.
  await ui.clickOnBreadcrumb(/Foo$/);
  expect(await ui.childComponent(/folderA/).find()).toBeInTheDocument();

  // Open "index.tsx" file using keyboard navigation.
  await ui.arrowDown();
  await ui.arrowDown();
  await act(async () => {
    await ui.arrowRight();
    // Load source viewer.
    expect((await ui.sourceCode.findAll()).length).toEqual(DEFAULT_LINES_LOADED);
  });

  // Navigate back using keyboard.
  await act(async () => {
    await ui.arrowLeft();
  });
  expect(await ui.childComponent(/folderA/).find()).toBeInTheDocument();
});

it('should behave correctly when using search', async () => {
  const ui = getPageObject(userEvent.setup());
  renderCode({
    navigateTo: `code?id=foo&search=nonexistent`,
  });
  await ui.appLoaded();

  // Starts with a query from the URL.
  expect(await ui.noResultsTxt.find()).toBeInTheDocument();
  await ui.clearSearch();

  // Search with results that are deeper than the current level.
  await ui.searchForComponent('out');
  expect(ui.childComponent(/out\.tsx/).get()).toBeInTheDocument();

  // Search with no results.
  await ui.searchForComponent('nonexistent');
  expect(await ui.noResultsTxt.find()).toBeInTheDocument();
  await ui.clearSearch();

  // Open file using keyboard navigation.
  await ui.searchForComponent('index');
  await ui.arrowDown();
  await ui.arrowDown();
  await act(async () => {
    await ui.arrowRight();
    // Load source viewer.
    expect((await ui.sourceCode.findAll()).length).toEqual(DEFAULT_LINES_LOADED);
  });

  // Navigate back using keyboard.
  await act(async () => {
    await ui.arrowLeft();
  });
  expect(await ui.childComponent(/folderA/).find()).toBeInTheDocument();
});

it('should correcly handle long lists of components', async () => {
  const component = mockComponent(componentsHandler.findComponentTree('foo')?.component);
  componentsHandler.registerComponentTree({
    component,
    ancestors: [],
    children: times(300, (n) => ({
      component: mockComponent({
        key: `foo:file${n}`,
        name: `file${n}`,
        qualifier: ComponentQualifier.File,
      }),
      ancestors: [component],
      children: [],
    })),
  });
  const ui = getPageObject(userEvent.setup());
  renderCode();
  await ui.appLoaded();

  expect(ui.showingOutOfTxt(100, 300).get()).toBeInTheDocument();
  await ui.clickLoadMore();
  expect(ui.showingOutOfTxt(200, 300).get()).toBeInTheDocument();
});

it.each([
  ComponentQualifier.Application,
  ComponentQualifier.Project,
  ComponentQualifier.Portfolio,
  ComponentQualifier.SubPortfolio,
])('should render correctly when there are no child components for %s', async (qualifier) => {
  const component = mockComponent({
    ...componentsHandler.findComponentTree('foo')?.component,
    qualifier,
    canBrowseAllChildProjects: true,
  });
  componentsHandler.registerComponentTree({
    component,
    ancestors: [],
    children: [],
  });
  const ui = getPageObject(userEvent.setup());
  renderCode({ component });

  expect(await ui.componentIsEmptyTxt(qualifier).find()).toBeInTheDocument();
});

it.each([ComponentQualifier.Portfolio, ComponentQualifier.SubPortfolio])(
  'should render a warning when not having access to all children for %s',
  async (qualifier) => {
    const ui = getPageObject(userEvent.setup());
    renderCode({
      component: mockComponent({
        ...componentsHandler.findComponentTree('foo')?.component,
        qualifier,
        canBrowseAllChildProjects: false,
      }),
    });

    expect(await ui.notAccessToAllChildrenTxt.find()).toBeInTheDocument();
  }
);

it('should correctly show measures for a project', async () => {
  const component = mockComponent(componentsHandler.findComponentTree('foo')?.component);
  componentsHandler.registerComponentTree({
    component,
    ancestors: [],
    children: [
      {
        component: mockComponent({
          key: 'folderA',
          name: 'folderA',
          qualifier: ComponentQualifier.Directory,
        }),
        measures: generateMeasures('2.0'),
        ancestors: [component],
        children: [],
      },
      {
        component: mockComponent({
          key: 'index.tsx',
          name: 'index.tsx',
        }),
        measures: [],
        ancestors: [component],
        children: [],
      },
    ],
  });
  const ui = getPageObject(userEvent.setup());
  renderCode();
  await ui.appLoaded(component.name);

  // Folder A
  const folderRow = ui.measureRow(/folderA/).get();
  [
    ['ncloc', '2'],
    ['bugs', '2'],
    ['vulnerabilities', '2'],
    ['code_smells', '2'],
    ['security_hotspots', '2'],
    ['coverage', '2.0%'],
    ['duplicated_lines_density', '2.0%'],
  ].forEach(([domain, value]) => {
    expect(ui.measureValueCell(folderRow, domain, value)).toBeInTheDocument();
  });

  // index.tsx
  const fileRow = ui.measureRow(/index\.tsx/).get();
  [
    ['ncloc', '—'],
    ['bugs', '—'],
    ['vulnerabilities', '—'],
    ['code_smells', '—'],
    ['security_hotspots', '—'],
    ['coverage', '—'],
    ['duplicated_lines_density', '—'],
  ].forEach(([domain, value]) => {
    expect(ui.measureValueCell(fileRow, domain, value)).toBeInTheDocument();
  });
});

it('should correctly show new VS overall measures for Portfolios', async () => {
  const component = mockComponent({
    key: 'portfolio',
    name: 'Portfolio',
    qualifier: ComponentQualifier.Portfolio,
    canBrowseAllChildProjects: true,
  });
  componentsHandler.registerComponentTree({
    component,
    measures: generateMeasures('1.0', '2.0'),
    ancestors: [],
    children: [
      {
        component: mockComponent({
          analysisDate: '2022-02-01',
          key: 'child1',
          name: 'Child 1',
        }),
        measures: generateMeasures('2.0', '3.0'),
        ancestors: [component],
        children: [],
      },
      {
        component: mockComponent({
          key: 'child2',
          name: 'Child 2',
        }),
        measures: [
          mockMeasure({ metric: MetricKey.alert_status, value: 'ERROR', period: undefined }),
        ],
        ancestors: [component],
        children: [],
      },
    ],
  });
  const ui = getPageObject(userEvent.setup());
  renderCode({ component });
  await ui.appLoaded(component.name);

  // New code measures.
  expect(ui.newCodeBtn.get()).toHaveAttribute('aria-current', 'true');

  // Child 1
  let child1Row = ui.measureRow(/^Child 1/).get();
  [
    ['Releasability', 'OK'],
    ['Reliability', 'C'],
    ['vulnerabilities', 'C'],
    ['security_hotspots', 'C'],
    ['Maintainability', 'C'],
    ['ncloc', '3'],
    ['last_analysis_date', '2022-02-01'],
  ].forEach(([domain, value]) => {
    expect(ui.measureValueCell(child1Row, domain, value)).toBeInTheDocument();
  });

  // Child 2
  let child2Row = ui.measureRow(/^Child 2/).get();
  [
    ['Releasability', 'ERROR'],
    ['Reliability', '—'],
    ['vulnerabilities', '—'],
    ['security_hotspots', '—'],
    ['Maintainability', '—'],
    ['ncloc', '—'],
    ['last_analysis_date', '—'],
  ].forEach(([domain, value]) => {
    expect(ui.measureValueCell(child2Row, domain, value)).toBeInTheDocument();
  });

  // Overall code measures
  await ui.showOverallCode();

  // Child 1
  child1Row = ui.measureRow(/^Child 1/).get();
  [
    ['Releasability', 'OK'],
    ['Reliability', 'B'],
    ['vulnerabilities', 'B'],
    ['security_hotspots', 'B'],
    ['Maintainability', 'B'],
    ['ncloc', '2'],
  ].forEach(([domain, value]) => {
    expect(ui.measureValueCell(child1Row, domain, value)).toBeInTheDocument();
  });

  // Child 2
  child2Row = ui.measureRow(/^Child 2/).get();
  [
    ['Releasability', 'ERROR'],
    ['Reliability', '—'],
    ['vulnerabilities', '—'],
    ['security_hotspots', '—'],
    ['Maintainability', '—'],
    ['ncloc', '—'],
  ].forEach(([domain, value]) => {
    expect(ui.measureValueCell(child2Row, domain, value)).toBeInTheDocument();
  });
});

function getPageObject(user: UserEvent) {
  const ui = {
    componentName: (name: string) => byText(name),
    childComponent: (name: string | RegExp) => byRole('cell', { name, exact: false }),
    componentIsEmptyTxt: (qualifier: ComponentQualifier) =>
      byText(`code_viewer.no_source_code_displayed_due_to_empty_analysis.${qualifier}`),
    searchInput: byRole('searchbox'),
    noResultsTxt: byText('no_results'),
    sourceCode: byText('function Test() {}'),
    notAccessToAllChildrenTxt: byText('code_viewer.not_all_measures_are_shown'),
    showingOutOfTxt: (x: number, y: number) => byText(`x_of_y_shown.${x}.${y}`),
    newCodeBtn: byRole('button', { name: 'projects.view.new_code' }),
    overallCodeBtn: byRole('button', { name: 'projects.view.overall_code' }),
    measureRow: (name: string | RegExp) => byRole('row', { name, exact: false }),
    measureValueCell: (row: HTMLElement, name: string, value: string) => {
      const i = Array.from(screen.getAllByRole('columnheader')).findIndex((c) =>
        c.textContent?.includes(name)
      );
      if (i < 0) {
        // eslint-disable-next-line testing-library/no-debugging-utils
        screen.debug(screen.getByRole('table'), 40000);
        throw new Error(`Couldn't locate column with header ${name}`);
      }

      const { getAllByRole } = within(row);
      const cell = getAllByRole('cell').at(i);

      if (cell === undefined) {
        throw new Error(`Couldn't locate cell with value ${value} for header ${name}`);
      }

      return within(cell).getByText(value);
    },
  };

  return {
    ...ui,
    async searchForComponent(text: string) {
      await user.type(ui.searchInput.get(), text);
    },
    async clearSearch() {
      await user.clear(ui.searchInput.get());
    },
    async clickOnChildComponent(name: string | RegExp) {
      await user.click(screen.getByRole('link', { name }));
    },
    async appLoaded(name = 'Foo') {
      await waitFor(() => {
        expect(ui.componentName(name).get()).toBeInTheDocument();
      });
    },
    async clickOnBreadcrumb(name: string | RegExp) {
      await user.click(screen.getByRole('link', { name }));
    },
    async arrowDown() {
      await user.keyboard('[ArrowDown]');
    },
    async arrowRight() {
      await user.keyboard('[ArrowRight]');
    },
    async arrowLeft() {
      await user.keyboard('[ArrowLeft]');
    },
    async clickLoadMore() {
      await user.click(screen.getByRole('button', { name: 'show_more' }));
    },
    async showOverallCode() {
      await user.click(ui.overallCodeBtn.get());
    },
  };
}

function generateMeasures(overallValue = '1.0', newValue = '2.0') {
  return [
    ...Object.values(MetricKey)
      .filter((metric) => metric !== MetricKey.alert_status)
      .map((metric) =>
        isDiffMetric(metric)
          ? mockMeasure({ metric, period: { index: 1, value: newValue } })
          : mockMeasure({ metric, value: overallValue, period: undefined })
      ),
    mockMeasure({
      metric: MetricKey.alert_status,
      value: overallValue === '1.0' || overallValue === '2.0' ? 'OK' : 'ERROR',
      period: undefined,
    }),
  ];
}

function renderCode({
  component = componentsHandler.findComponentTree('foo')?.component,
  navigateTo,
}: { component?: Component; navigateTo?: string } = {}) {
  return renderAppWithComponentContext('code', routes, { navigateTo }, { component });
}
