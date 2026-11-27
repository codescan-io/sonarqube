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

import { ToastOptions } from 'react-toastify';
import { addGlobalErrorMessage } from '~design-system';
import handleRequiredAuthentication from '../../helpers/handleRequiredAuthentication';
import {
  IP_ALLOWLIST_TOAST_ID,
  getIpAllowlistBlockedMessage,
  isIpAllowlistToastSuppressed,
} from '../../helpers/ipAllowlist';
import { HttpStatus, parseError } from '../../helpers/request';

interface ThrowGlobalErrorOptions {
  redirectUnauthorizedNoReasons?: boolean;
  returnErrorReasons?: boolean; // used only in SC
}

export function throwGlobalError(
  param: Response | any,
  options: ThrowGlobalErrorOptions = {},
): Promise<Response | any> {
  if (param.response instanceof Response) {
    /* eslint-disable-next-line no-console */
    console.warn('DEPRECATED: response should not be wrapped, pass it directly.');
    param = param.response;
  }

  if (param instanceof Response) {
    const response = param;
    return getIpAllowlistBlockedMessage(response).then((blockedMessage) => {
      if (blockedMessage === undefined) {
        return showMessageThenReject(response, options);
      }
      // The blocked page already says this; a toast on top of it would only repeat it. Anywhere else
      // the toast is the only thing that tells the user, and it carries an id so that the redirect
      // can remove just this one.
      return isIpAllowlistToastSuppressed()
        ? Promise.reject(response)
        : showMessageThenReject(response, options, { toastId: IP_ALLOWLIST_TOAST_ID });
    });
  }

  // Axios response object
  if (param.data?.message) {
    addGlobalErrorMessage(param.data?.message);

    return Promise.reject(param);
  }

  return Promise.reject(param);
}

function showMessageThenReject(
  response: Response,
  options: ThrowGlobalErrorOptions,
  toastOptions?: ToastOptions,
): Promise<never> {
  return parseError(response)
    .then(
      (message) => {
        if (toastOptions) {
          addGlobalErrorMessage(message, toastOptions);
        } else {
          addGlobalErrorMessage(message);
        }
        if (options.redirectUnauthorizedNoReasons && response.status === HttpStatus.Unauthorized) {
          handleRequiredAuthentication();
        }
      },
      () => {
        /* ignore parsing errors */
      },
    )
    .then(() => Promise.reject(response));
}
