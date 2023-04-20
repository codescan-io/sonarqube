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
import { injectIntl, WrappedComponentProps } from 'react-intl';
import DateInput from '../../../components/controls/DateInput';
import FacetBox from '../../../components/facet/FacetBox';
import FacetHeader from '../../../components/facet/FacetHeader';
import { longFormatterOption } from '../../../components/intl/DateFormatter';
import { translate } from '../../../helpers/l10n';
import { Query } from '../query';

interface Props {
  onChange: (changes: Partial<Query>) => void;
  onToggle: (property: keyof Query) => void;
  open: boolean;
  value?: Date;
}

class AvailableSinceFacet extends React.PureComponent<Props & WrappedComponentProps> {
  handleHeaderClick = () => {
    this.props.onToggle('availableSince');
  };

  handleClear = () => {
    this.props.onChange({ availableSince: undefined });
  };

  handlePeriodChange = (date: Date | undefined) => {
    this.props.onChange({ availableSince: date });
  };

  getValues = () =>
    this.props.value
      ? [this.props.intl.formatDate(this.props.value, longFormatterOption)]
      : undefined;

  render() {
    const { open, value } = this.props;

    return (
      <FacetBox property="availableSince">
        <FacetHeader
          name={translate('coding_rules.facet.available_since')}
          onClear={this.handleClear}
          onClick={this.handleHeaderClick}
          open={open}
          values={this.getValues()}
        />

        {open && (
          <DateInput
            name="available-since"
            onChange={this.handlePeriodChange}
            placeholder={translate('date')}
            value={value}
          />
        )}
      </FacetBox>
    );
  }
}

export default injectIntl(AvailableSinceFacet);
