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

import * as React from 'react';
import { getOrganization } from '../../../api/organizations';
import { RuleDetails } from '../../../types/types';
import CustomRuleFormModal from './CustomRuleFormModal';
import AIRuleWizard from './ai-custom-rules/AIRuleWizard';
import CreateRuleSelectionModal from './ai-custom-rules/CreateRuleSelectionModal';

interface Props {
  children: (props: { onClick: () => void }) => React.ReactNode;
  customRule?: RuleDetails;
  templateRule: RuleDetails;
  organization: string;
}

export default function CustomRuleButton(props: Props) {
  const { customRule, templateRule } = props;
  const [selectionModalOpen, setSelectionModalOpen] = React.useState(false);
  const [xpathModalOpen, setXpathModalOpen] = React.useState(false);
  const [aiWizardOpen, setAiWizardOpen] = React.useState(false);
  const [aiCustomRulesEnabled, setAiCustomRulesEnabled] = React.useState(false);

  // AI custom rules are only available for Salesforce Metadata (sfmeta) template rules
  const isSfMetaRule = templateRule.repo?.includes('sfmeta') ?? false;

  React.useEffect(() => {
    if (props.organization && isSfMetaRule) {
      getOrganization(props.organization).then((org) => {
        setAiCustomRulesEnabled(org.aiCustomRulesEnabled || false);
      }).catch(() => {
        setAiCustomRulesEnabled(false);
      });
    }
  }, [props.organization, isSfMetaRule]);

  const handleClick = () => {
    if (isSfMetaRule) {
      setSelectionModalOpen(true);
    } else {
      setXpathModalOpen(true);
    }
  };

  const handleSelectXPath = () => {
    setSelectionModalOpen(false);
    setXpathModalOpen(true);
  };

  const handleSelectAI = () => {
    setSelectionModalOpen(false);
    setAiWizardOpen(true);
  };

  return (
    <>
      {props.children({ onClick: handleClick })}

      {/* Step 1: Selection Modal (shown for sfmeta rules; AI option gated by the org flag) */}
      {isSfMetaRule && (
        <CreateRuleSelectionModal
          isOpen={selectionModalOpen}
          aiEnabled={aiCustomRulesEnabled}
          onClose={() => setSelectionModalOpen(false)}
          onSelectAI={handleSelectAI}
          onSelectXPath={handleSelectXPath}
        />
      )}

      {/* Step 2a: XPath Modal (Existing) */}
      {xpathModalOpen && (
        <CustomRuleFormModal
          organization={props.organization}
          customRule={customRule}
          onClose={() => setXpathModalOpen(false)}
          templateRule={templateRule}
          isOpen={xpathModalOpen}
        />
      )}

      {/* Step 2b: AI Rule Wizard (only when AI is enabled) */}
      {aiCustomRulesEnabled && (
        <AIRuleWizard
          isOpen={aiWizardOpen}
          onClose={() => setAiWizardOpen(false)}
          onBack={() => {
            setAiWizardOpen(false);
            setSelectionModalOpen(true);
          }}
          organization={props.organization}
          templateRule={templateRule}
        />
      )}
    </>
  );
}
