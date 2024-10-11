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
import tw from 'twin.macro';
import { themeBorder, themeColor } from '../helpers/theme';
import { isDefined } from '../helpers/types';
import { Highlighter, RegisteredLanguages } from './Highlighter';
import { ClipboardButton } from './clipboard';

interface Props {
  className?: string;
  highlight?: boolean;
  isOneLine?: boolean;
  join?: string;
  language?: RegisteredLanguages;
  noCopy?: boolean;
  render?: string;
  snippet: string | Array<string | undefined>;
  toggleEdit?: VoidFunction;
  wrap?: boolean;
}

// keep this "useless" concatenation for the readability reason
// eslint-disable-next-line no-useless-concat
const s = ' \\' + '\n  ';

export function CodeSnippet(props: Props) {
  const {
    className,
    isOneLine,
    highlight,
    join = s,
    language,
    noCopy,
    render,
    snippet,
    toggleEdit,
    wrap,
  } = props;
  const snippetArray = Array.isArray(snippet) ? snippet.filter(isDefined) : [snippet];
  const finalSnippet = isOneLine ? snippetArray.join(' ') : snippetArray.join(join);

  const isSimpleOneLine = isOneLine && noCopy;

  const copyButton = isOneLine ? (
    <StyledSingleLineClipboardButton copyValue={finalSnippet} />
  ) : (
    <StyledClipboardButton copyValue={finalSnippet} />
  );

  return (
    <Wrapper
      className={classNames(
        {
          'code-snippet-highlighted-oneline': isOneLine,
          'code-snippet-simple-oneline': isSimpleOneLine,
        },
        className,
        'fs-mask'
      )}
    >
      {!noCopy && copyButton}
      <Highlighter
        code={render ?? finalSnippet}
        highlight={highlight}
        isSimpleOneLine={isSimpleOneLine}
        language={language}
        toggleEdit={isOneLine ? toggleEdit : undefined}
        wrap={wrap}
      />
    </Wrapper>
  );
}

const Wrapper = styled.div`
  background-color: ${themeColor('codeSnippetBackground')};
  border: ${themeBorder('default', 'codeSnippetBorder')};

  ${tw`sw-rounded-2`}
  ${tw`sw-relative`}
  ${tw`sw-my-2`}

  &.code-snippet-simple-oneline {
    ${tw`sw-my-0`}
    ${tw`sw-rounded-1`}
  }
`;

const StyledClipboardButton = styled(ClipboardButton)`
  ${tw`sw-select-none`}
  ${tw`sw-body-sm`}
  ${tw`sw-top-6 sw-right-6`}
  ${tw`sw-absolute`}

  .code-snippet-highlighted-oneline & {
    ${tw`sw-bottom-2`}
  }
`;

const StyledSingleLineClipboardButton = styled(StyledClipboardButton)`
  ${tw`sw-top-6 sw-bottom-6`}
`;
