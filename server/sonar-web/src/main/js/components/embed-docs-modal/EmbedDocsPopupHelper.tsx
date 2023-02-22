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
  Dropdown,
  InteractiveIcon,
  MenuHelpIcon,
  PopupPlacement,
  PopupZLevel,
  Tooltip,
} from 'design-system';
import * as React from 'react';
import { translate } from '../../helpers/l10n';
import EmbedDocsPopup from './EmbedDocsPopup';
import Modal from "../controls/Modal";
import { getBaseUrl } from "../../helpers/system";
import { ClearButton } from "../controls/buttons";

export default function EmbedDocsPopupHelper() {

  const [aboutCodescanOpen, setAboutCodescanOpen] = React.useState(false);

  const renderAboutCodescan = (link: string, icon: string, text: string) => {
    return (
      <Modal
        className="abs-width-auto"
        onRequestClose={() => setAboutCodescanOpen(false)}
        contentLabel=''
      >
        <a href={link} rel="noopener noreferrer" target="_blank">
          <img alt={text} src={`${getBaseUrl()}/images/${icon}`}/>
        </a>
        <span className="cross-button">
          <ClearButton onClick={() => setAboutCodescanOpen(false)}/>
        </span>
      </Modal>
    );
  }

  console.info('aboutCodescanOpen', aboutCodescanOpen)

  return (
    <div className="dropdown">
      <Dropdown
        id="help-menu-dropdown"
        placement={PopupPlacement.BottomRight}
        overlay={<EmbedDocsPopup setAboutCodescanOpen={setAboutCodescanOpen} />}
        allowResizing={true}
        zLevel={PopupZLevel.Global}
      >
        {({ onToggleClick, open }) => (
          <Tooltip
            mouseLeaveDelay={0.2}
            overlay={translate('help')}
            visible={open ? false : undefined}
          >
            <InteractiveIcon
              Icon={MenuHelpIcon}
              aria-expanded={open}
              aria-controls="help-menu-dropdown"
              aria-haspopup={true}
              aria-label={translate('help')}
              currentColor={true}
              onClick={onToggleClick}
              size="medium"
              stopPropagation={false}
            />
          </Tooltip>
        )}
      </Dropdown>

      {aboutCodescanOpen && renderAboutCodescan(
        'https://knowledgebase.autorabit.com/codescan/docs/codescan-release-notes',
        'embed-doc/codescan-version-24_0_11.png',
        translate('embed_docs.codescan_version')
      )}
    </div>
  );
}
