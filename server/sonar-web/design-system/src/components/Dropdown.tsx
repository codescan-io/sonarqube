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
import React from 'react';
import { translate } from '../helpers/l10n';
import { PopupPlacement, PopupZLevel } from '../helpers/positioning';
import { InputSizeKeys } from '../types/theme';
import { DropdownMenu } from './DropdownMenu';
import DropdownToggler from './DropdownToggler';
import MenuIcon from './icons/MenuIcon';
import { InteractiveIcon } from './InteractiveIcon';

type OnClickCallback = (event?: React.MouseEvent<HTMLElement>) => void;
type A11yAttrs = Pick<React.AriaAttributes, 'aria-controls' | 'aria-expanded' | 'aria-haspopup'> & {
  id: string;
  role: React.AriaRole;
};
interface RenderProps {
  a11yAttrs: A11yAttrs;
  closeDropdown: VoidFunction;
  onToggleClick: OnClickCallback;
  open: boolean;
}

interface Props {
  allowResizing?: boolean;
  children:
    | ((renderProps: RenderProps) => JSX.Element)
    | React.ReactElement<{ onClick: OnClickCallback }>;
  className?: string;
  closeOnClick?: boolean;
  id: string;
  isPortal?: boolean;
  onOpen?: VoidFunction;
  overlay: React.ReactNode;
  placement?: PopupPlacement;
  size?: InputSizeKeys;
  zLevel?: PopupZLevel;
}

interface State {
  open: boolean;
}

export default class Dropdown extends React.PureComponent<Props, State> {
  state: State = { open: false };

  componentDidUpdate(_: Props, prevState: State) {
    if (!prevState.open && this.state.open && this.props.onOpen) {
      this.props.onOpen();
    }
  }

  handleClose = () => {
    this.setState({ open: false });
  };

  handleToggleClick: OnClickCallback = (event) => {
    if (event) {
      event.preventDefault();
      event.currentTarget.blur();
    }
    this.setState((state) => ({ open: !state.open }));
  };

  render() {
    const { open } = this.state;
    const {
      allowResizing,
      className,
      closeOnClick = true,
      id,
      isPortal,
      size = 'full',
      zLevel,
    } = this.props;
    const a11yAttrs: A11yAttrs = {
      'aria-controls': `${id}-dropdown`,
      'aria-expanded': open,
      'aria-haspopup': 'menu',
      id: `${id}-trigger`,
      role: 'button',
    };

    const children = React.isValidElement(this.props.children)
      ? React.cloneElement(this.props.children, { onClick: this.handleToggleClick, ...a11yAttrs })
      : this.props.children({
          a11yAttrs,
          closeDropdown: this.handleClose,
          onToggleClick: this.handleToggleClick,
          open,
        });

    return (
      <DropdownToggler
        allowResizing={allowResizing}
        aria-labelledby={`${id}-trigger`}
        className={className}
        id={`${id}-dropdown`}
        isPortal={isPortal}
        onRequestClose={this.handleClose}
        open={open}
        overlay={
          <DropdownMenu onClick={closeOnClick ? this.handleClose : undefined} size={size}>
            {this.props.overlay}
          </DropdownMenu>
        }
        placement={this.props.placement}
        zLevel={zLevel}
      >
        {children}
      </DropdownToggler>
    );
  }
}

interface ActionsDropdownProps extends Omit<Props, 'children' | 'overlay'> {
  buttonSize?: 'small' | 'medium';
  children: React.ReactNode;
}

export function ActionsDropdown(props: ActionsDropdownProps) {
  const { children, buttonSize, ...dropdownProps } = props;
  return (
    <Dropdown overlay={children} {...dropdownProps}>
      <InteractiveIcon
        Icon={MenuIcon}
        aria-label={translate('menu')}
        size={buttonSize}
        stopPropagation={false}
      />
    </Dropdown>
  );
}
