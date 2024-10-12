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
import { FlagMessage, HtmlFormatter, themeBorder, themeColor, ToggleButton } from 'design-system';
import * as React from 'react';
import { RuleDescriptionSection } from '../../apps/coding-rules/rule';
import applyCodeDifferences from '../../helpers/code-difference';
import { translate, translateWithParameters } from '../../helpers/l10n';
import { sanitizeString } from '../../helpers/sanitize';
import OtherContextOption from './OtherContextOption';

const OTHERS_KEY = 'others';

interface Props {
  sections: RuleDescriptionSection[];
  defaultContextKey?: string;
  className?: string;
}

interface State {
  contexts: RuleDescriptionContextDisplay[];
  defaultContext?: RuleDescriptionContextDisplay;
  selectedContext?: RuleDescriptionContextDisplay;
}

interface RuleDescriptionContextDisplay {
  displayName: string;
  content: string;
  key: string;
}

export default class RuleDescription extends React.PureComponent<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = this.computeState();
  }

  componentDidUpdate(prevProps: Props) {
    const { sections, defaultContextKey } = this.props;

    if (prevProps.sections !== sections || prevProps.defaultContextKey !== defaultContextKey) {
      this.setState(this.computeState());
    }
  }

  computeState = () => {
    const { sections, defaultContextKey } = this.props;

    const contexts = sections
      .filter(
        (
          section
        ): section is RuleDescriptionSection & Required<Pick<RuleDescriptionSection, 'context'>> =>
          section.context != null
      )
      .map((section) => ({
        displayName: section.context.displayName || section.context.key,
        content: section.content,
        key: section.context.key,
      }))
      .sort((a, b) => a.displayName.localeCompare(b.displayName));

    if (contexts.length > 0) {
      contexts.push({
        displayName: translate('coding_rules.description_context.other'),
        content: '',
        key: OTHERS_KEY,
      });
    }

    let defaultContext: RuleDescriptionContextDisplay | undefined;

    if (defaultContextKey) {
      defaultContext = contexts.find((context) => context.key === defaultContextKey);
    }

    return {
      contexts,
      defaultContext,
      selectedContext: defaultContext ?? contexts[0],
    };
  };

  handleToggleContext = (value: string) => {
    const { contexts } = this.state;

    const selected = contexts.find((ctxt) => ctxt.displayName === value);
    if (selected) {
      this.setState({ selectedContext: selected });
    }
  };

  render() {
    const { className, sections } = this.props;
    const { contexts, defaultContext, selectedContext } = this.state;

    const options = contexts.map((ctxt) => ({
      label: ctxt.displayName,
      value: ctxt.displayName,
    }));

    if (contexts.length > 0 && selectedContext) {
      return (
        <StyledHtmlFormatter
          className={className}
          ref={(node: HTMLDivElement) => {
            applyCodeDifferences(node);
          }}
        >
          <h2 className="sw-body-sm-highlight sw-mb-4">
            {translate('coding_rules.description_context.title')}
          </h2>
          {defaultContext && (
            <FlagMessage variant="info" className="sw-mb-4">
              {translateWithParameters(
                'coding_rules.description_context.default_information',
                defaultContext.displayName
              )}
            </FlagMessage>
          )}
          <div className="sw-mb-4">
            <ToggleButton
              label={translate('coding_rules.description_context.title')}
              onChange={this.handleToggleContext}
              options={options}
              value={selectedContext.displayName}
            />
            {selectedContext.key !== OTHERS_KEY && (
              <h2>
                {translateWithParameters(
                  'coding_rules.description_context.sub_title',
                  selectedContext.displayName
                )}
              </h2>
            )}
          </div>
          {selectedContext.key === OTHERS_KEY ? (
            <OtherContextOption />
          ) : (
            <div
              /* eslint-disable-next-line react/no-danger */
              dangerouslySetInnerHTML={{ __html: sanitizeString(selectedContext.content) }}
            />
          )}
        </StyledHtmlFormatter>
      );
    }

    return (
      <StyledHtmlFormatter
        className={className}
        ref={(node: HTMLDivElement) => {
          applyCodeDifferences(node);
        }}
        // eslint-disable-next-line react/no-danger
        dangerouslySetInnerHTML={{
          __html: sanitizeString(sections[0].content),
        }}
      />
    );
  }
}

const StyledHtmlFormatter = styled(HtmlFormatter)`
  margin-top: 1.5rem;
  margin-bottom: 1.5rem;

  .code-difference-container {
    display: flex;
    flex-direction: column;
    width: fit-content;
    min-width: 100%;
  }

  .code-difference-scrollable {
    background-color: ${themeColor('codeSnippetBackground')};
    border: ${themeBorder('default', 'codeSnippetBorder')};
    border-radius: 0.5rem;
    padding: 1.5rem;
    overflow-x: auto;
  }

  .code-difference-scrollable .code-added,
  .code-difference-scrollable .code-removed {
    padding-left: 1.5rem;
    margin-left: -1.5rem;
    padding-right: 1.5rem;
    margin-right: -1.5rem;
    border-radius: 0;
  }

  .code-difference-scrollable .code-added {
    background-color: ${themeColor('codeLineCoveredUnderline')};
  }

  .code-difference-scrollable .code-removed {
    background-color: ${themeColor('codeLineUncoveredUnderline')};
  }
`;
