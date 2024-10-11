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
import styled from '@emotion/styled';
import classNames from 'classnames';
import { noop } from 'lodash';
import tw from 'twin.macro';
import { translate } from '../helpers/l10n';
import { themeBorder, themeColor, themeContrast, themeShadow } from '../helpers/theme';
import { RadioButton } from './RadioButton';
import { LightLabel } from './Text';
import { RecommendedIcon } from './icons/RecommendedIcon';

export interface SelectionCardProps {
  children?: React.ReactNode;
  className?: string;
  disabled?: boolean;
  onClick?: VoidFunction;
  recommended?: boolean;
  recommendedReason?: string;
  selected?: boolean;
  title: string;
  titleInfo?: React.ReactNode;
  vertical?: boolean;
}

export function SelectionCard(props: SelectionCardProps) {
  const {
    children,
    className,
    disabled,
    onClick,
    recommended,
    recommendedReason,
    selected = false,
    title,
    titleInfo,
    vertical = false,
  } = props;
  const isActionable = Boolean(onClick);
  return (
    <Wrapper
      className={classNames(
        'js-radio-card',
        {
          'card-actionable': isActionable && !disabled,
          'card-vertical': vertical,
          disabled,
          selected,
        },
        className
      )}
      onClick={isActionable && !disabled ? onClick : undefined}
      tabIndex={0}
    >
      <Content>
        {isActionable && (
          <div className="sw-items-start sw-mt-1/2 sw-mr-2">
            <RadioButton checked={selected} disabled={disabled} onCheck={noop} value={title} />
          </div>
        )}
        <div>
          <Header>
            {title}
            <LightLabel>{titleInfo}</LightLabel>
          </Header>
          <Body>{children}</Body>
        </div>
      </Content>
      {recommended && (
        <Recommended>
          <StyledRecommendedIcon className="sw-mr-1" />
          <span className="sw-align-middle">
            <strong>{translate('recommended')}</strong> {recommendedReason}
          </span>
        </Recommended>
      )}
    </Wrapper>
  );
}

const Wrapper = styled.div`
  ${tw`sw-relative sw-flex sw-flex-col`}
  ${tw`sw-rounded-2`}
  ${tw`sw-box-border`}

  background-color: ${themeColor('backgroundSecondary')};
  border: ${themeBorder('default', 'selectionCardBorder')};

  &:focus {
    outline: none;
  }

  &.card-vertical {
    ${tw`sw-w-full`}
    min-height: auto;
  }

  &.card-actionable {
    ${tw`sw-cursor-pointer`}

    &:hover {
      border: ${themeBorder('default', 'selectionCardBorderHover')};
      box-shadow: ${themeShadow('sm')};
    }

    &.selected {
      border: ${themeBorder('default', 'selectionCardBorderSelected')};
    }
  }

  &.disabled {
    ${tw`sw-cursor-not-allowed`}

    background-color: ${themeColor('selectionCardDisabled')};
    border: ${themeBorder('default', 'selectionCardBorderDisabled')};
  }
`;

const Content = styled.div`
  ${tw`sw-my-4 sw-mx-3`}
  ${tw`sw-flex sw-grow`}
`;

const Recommended = styled.div`
  ${tw`sw-body-sm`}
  ${tw`sw-py-2 sw-px-4`}
  ${tw`sw-box-border`}
  ${tw`sw-rounded-b-2`}

  color: ${themeContrast('infoBackground')};
  background-color: ${themeColor('infoBackground')};
`;

const StyledRecommendedIcon = styled(RecommendedIcon)`
  color: ${themeColor('iconInfo')};
  ${tw`sw-align-middle`}
`;

const Header = styled.h2`
  ${tw`sw-flex sw-items-center`}
  ${tw`sw-mb-3 sw-gap-2`}
  ${tw`sw-body-sm-highlight`}

  color: ${themeColor('selectionCardHeader')};

  .disabled & {
    color: ${themeContrast('selectionCardDisabled')};
  }
`;

const Body = styled.div`
  ${tw`sw-flex sw-grow`}
  ${tw`sw-flex-col sw-justify-between`}
`;
