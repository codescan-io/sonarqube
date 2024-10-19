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
import { SubnavigationGroup, SubnavigationItem } from 'design-system';
import { sortBy } from 'lodash';
import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import withAvailableFeatures, {
  WithAvailableFeaturesProps,
} from '../../../app/components/available-features/withAvailableFeatures';
import { translate } from '../../../helpers/l10n';
import { getGlobalSettingsUrl, getProjectSettingsUrl } from '../../../helpers/urls';
import { Feature } from '../../../types/features';
import { Component } from '../../../types/types';
import { CATEGORY_OVERRIDES } from '../constants';
import { getCategoryName } from '../utils';
import { ADDITIONAL_CATEGORIES } from './AdditionalCategories';
import { ALL_CUSTOMER_CATEGORIES } from './AllCustomerCategories';
import withAppStateContext from "../../../app/components/app-state/withAppStateContext";
import { AppState } from "../../../types/appstate";

export interface CategoriesListProps extends WithAvailableFeaturesProps {
  categories: string[];
  component?: Component;
  defaultCategory: string;
  selectedCategory: string;
  appState: AppState;
}

function CategoriesList(props: Readonly<CategoriesListProps>) {
  const { categories, component, defaultCategory, selectedCategory } = props;

  const navigate = useNavigate();

  const openCategory = React.useCallback(
    (category: string | undefined) => {
      const url = component
        ? getProjectSettingsUrl(component.key, category)
        : getGlobalSettingsUrl(category);

      navigate(url);
    },
    [component, navigate],
  );

  const categoriesWithName = categories
    .filter((key) => CATEGORY_OVERRIDES[key.toLowerCase()] === undefined)
    .map((key) => ({
      key,
      name: getCategoryName(key),
    }))
    .concat(
      ADDITIONAL_CATEGORIES.filter((c) => {
        const availableForCurrentMenu = component
          ? // Project settings
            c.availableForProject
          : // Global settings
            c.availableGlobally;

        return (
          c.displayTab &&
          availableForCurrentMenu &&
          (props.hasFeature(Feature.BranchSupport) || !c.requiresBranchSupport) &&
          (props.hasFeature(Feature.FixSuggestions) || c.key !== 'codefix')
        );
      }),
    );
  const sortedCategories = sortBy(categoriesWithName, (category) => category.name.toLowerCase());

  return (
    <SubnavigationGroup
      as="nav"
      aria-label={translate('settings.page')}
      className="sw-box-border it__subnavigation_menu"
    >
      {sortedCategories.map((c) => {
        const category = c.key !== defaultCategory ? c.key.toLowerCase() : undefined;
        const isActive = c.key.toLowerCase() === selectedCategory.toLowerCase();
        return (
          <SubnavigationItem
            active={isActive}
            ariaCurrent={isActive}
            onClick={() => openCategory(category)}
            key={c.key}
          >
            {c.name}
          </SubnavigationItem>
        );
      })}
    </SubnavigationGroup>
  );
}

export default withAppStateContext(withAvailableFeatures(CategoriesList));
