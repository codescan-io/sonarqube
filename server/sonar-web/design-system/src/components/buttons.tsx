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

import { css } from '@emotion/react';
import styled from '@emotion/styled';
import React from 'react';
import tw from 'twin.macro';
import { OPACITY_20_PERCENT } from '../helpers/constants';
import { themeBorder, themeColor, themeContrast } from '../helpers/theme';
import { ThemedProps } from '../types/theme';
import { BaseLink, LinkProps } from './Link';

type AllowedButtonAttributes = Pick<
  React.ButtonHTMLAttributes<HTMLButtonElement>,
  'aria-label' | 'autoFocus' | 'id' | 'name' | 'role' | 'style' | 'title' | 'type'
>;

export interface ButtonProps extends AllowedButtonAttributes {
  children?: React.ReactNode;
  className?: string;
  disabled?: boolean;
  icon?: React.ReactNode;
  innerRef?: React.Ref<HTMLButtonElement>;
  onClick?: VoidFunction;

  preventDefault?: boolean;
  reloadDocument?: LinkProps['reloadDocument'];
  stopPropagation?: boolean;
  target?: LinkProps['target'];
  to?: LinkProps['to'];
}

class Button extends React.PureComponent<ButtonProps> {
  handleClick = (event: React.MouseEvent<HTMLButtonElement | HTMLAnchorElement>) => {
    const { disabled, onClick, stopPropagation = false, type } = this.props;
    const { preventDefault = type !== 'submit' } = this.props;

    if (preventDefault || disabled) {
      event.preventDefault();
    }

    if (stopPropagation) {
      event.stopPropagation();
    }

    if (onClick && !disabled) {
      onClick();
    }
  };

  render() {
    const {
      children,
      disabled,
      icon,
      innerRef,
      onClick,
      preventDefault,
      stopPropagation,
      to,
      type = 'button',
      ...htmlProps
    } = this.props;

    const props = {
      ...htmlProps,
      'aria-disabled': disabled,
      disabled,
      type,
    };

    if (to) {
      return (
        <BaseButtonLink {...props} onClick={onClick} to={to}>
          {icon}
          {children}
        </BaseButtonLink>
      );
    }

    return (
      <BaseButton {...props} onClick={this.handleClick} ref={innerRef}>
        {icon}
        {children}
      </BaseButton>
    );
  }
}

const buttonStyle = (props: ThemedProps) => css`
  box-sizing: border-box;
  text-decoration: none;
  outline: none;
  border: var(--border);
  color: var(--color);
  background-color: var(--background);
  transition: background-color 0.2s ease, outline 0.2s ease;

  ${tw`sw-inline-flex sw-items-center`}
  ${tw`sw-h-control`}
  ${tw`sw-body-sm-highlight`}
  ${tw`sw-py-2 sw-px-4`}
  ${tw`sw-rounded-2`}
  ${tw`sw-cursor-pointer`}

  &:hover {
    color: var(--color);
    background-color: var(--backgroundHover);
  }

  &:focus,
  &:active {
    color: var(--color);
    outline: ${themeBorder('focus', 'var(--focus)')(props)};
  }

  &:disabled,
  &:disabled:hover {
    color: ${themeContrast('buttonDisabled')(props)};
    background-color: ${themeColor('buttonDisabled')(props)};
    border: ${themeBorder('default', 'buttonDisabledBorder')(props)};

    ${tw`sw-cursor-not-allowed`}
  }

  & > svg {
    ${tw`sw-mr-1`}
  }
`;

const BaseButtonLink = styled(BaseLink)`
  ${buttonStyle}
`;

const BaseButton = styled.button`
  ${buttonStyle}

  /* Workaround for tooltips issue with onMouseLeave in disabled buttons: https://github.com/facebook/react/issues/4251 */
  & [disabled] {
    ${tw`sw-pointer-events-none`};
  }
`;

export const ButtonPrimary: React.FC<ButtonProps> = styled(Button)`
  --background: ${themeColor('button')};
  --backgroundHover: ${themeColor('buttonHover')};
  --color: ${themeContrast('primary')};
  --focus: ${themeColor('button', OPACITY_20_PERCENT)};
  --border: ${themeBorder('default', 'transparent')};
`;

export const ButtonSecondary: React.FC<ButtonProps> = styled(Button)`
  --background: ${themeColor('buttonSecondary')};
  --backgroundHover: ${themeColor('buttonSecondaryHover')};
  --color: ${themeContrast('buttonSecondary')};
  --focus: ${themeColor('buttonSecondaryBorder', OPACITY_20_PERCENT)};
  --border: ${themeBorder('default', 'buttonSecondaryBorder')};
`;

export const DangerButtonPrimary: React.FC<ButtonProps> = styled(Button)`
  --background: ${themeColor('dangerButton')};
  --backgroundHover: ${themeColor('dangerButtonHover')};
  --color: ${themeContrast('dangerButton')};
  --focus: ${themeColor('dangerButtonFocus', OPACITY_20_PERCENT)};
  --border: ${themeBorder('default', 'transparent')};
`;

export const DangerButtonSecondary: React.FC<ButtonProps> = styled(Button)`
  --background: ${themeColor('dangerButtonSecondary')};
  --backgroundHover: ${themeColor('dangerButtonSecondaryHover')};
  --color: ${themeContrast('dangerButtonSecondary')};
  --focus: ${themeColor('dangerButtonSecondaryFocus', OPACITY_20_PERCENT)};
  --border: ${themeBorder('default', 'dangerButtonSecondaryBorder')};
`;

export const WrapperButton: React.FC<ButtonProps> = styled(Button)`
  --background: none;
  --backgroundHover: none;
  --color: none;
  --focus: ${themeColor('button', OPACITY_20_PERCENT)};
  --border: none;
`;

interface ThirdPartyProps extends Omit<ButtonProps, 'Icon'> {
  iconPath: string;
  name: string;
}

export function ThirdPartyButton({ children, iconPath, name, ...buttonProps }: ThirdPartyProps) {
  const size = 16;
  return (
    <ThirdPartyButtonStyled {...buttonProps}>
      <img alt={name} className="sw-mr-1" height={size} src={iconPath} width={size} />
      {children}
    </ThirdPartyButtonStyled>
  );
}

const ThirdPartyButtonStyled: React.FC<ButtonProps> = styled(Button)`
  --background: ${themeColor('thirdPartyButton')};
  --backgroundHover: ${themeColor('thirdPartyButtonHover')};
  --color: ${themeContrast('thirdPartyButton')};
  --focus: ${themeColor('thirdPartyButtonBorder', OPACITY_20_PERCENT)};
  --border: ${themeBorder('default', 'thirdPartyButtonBorder')};
`;

export const BareButton = styled.button`
  all: unset;
  cursor: pointer;

  &:focus-visible {
    background-color: ${themeColor('dropdownMenuHover')};
  }
`;
