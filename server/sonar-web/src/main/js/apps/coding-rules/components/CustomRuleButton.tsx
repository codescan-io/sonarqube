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
import { RuleDetails } from '../../../types/types';
import CustomRuleFormModal from './CustomRuleFormModal';
import CreateRuleSelectionModal from './ai-custom-rules/CreateRuleSelectionModal';
import AIRuleWizard from './ai-custom-rules/AIRuleWizard';

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
      {props.children({ onClick: () => setSelectionModalOpen(true) })}

      {/* Step 1: Selection Modal */}
      <CreateRuleSelectionModal
        isOpen={selectionModalOpen}
        onClose={() => setSelectionModalOpen(false)}
        onSelectAI={handleSelectAI}
        onSelectXPath={handleSelectXPath}
      />

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

      {/* Step 2b: AI Rule Wizard (Define → Generate → Review) */}
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
    </>
  );
}
