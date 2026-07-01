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

import { Button, ButtonVariety, Modal, ModalSize, Select, Text } from '@sonarsource/echoes-react';
import { SyntheticEvent, useState } from 'react';
import { FormField, InputField, InputTextArea, LabelValueSelectOption } from '~design-system';
import { RULE_TYPES } from '../../../../helpers/constants';
import { translate } from '../../../../helpers/l10n';
import { latinize } from '../../../../helpers/strings';
import { SeveritySelect } from '../SeveritySelect';
import SparklesSvg from './icons/sparkles.svg';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (values: AIRuleFormValues) => void;
  organization: string;
}

export interface AIRuleFormValues {
  ruleName: string;
  ruleKey: string;
  ruleType: string;
  severity: string;
  message: string;
  ruleDescription: string;
}

const MAX_CHAR_COUNT = 5000;
const MIN_CHAR_COUNT = 20;

const EXAMPLE_DESCRIPTIONS = [
  'SOQLs cannot be placed too close to any for loop',
  'Triggers must delegate to business logic to implement logic',
  'Apex classes should not exceed 1000 lines of code',
  'Apex class handling must not use the identified Apex/Logger framework',
];

export default function AIRuleFormModal(props: Readonly<Props>) {
  const { isOpen, onClose, onSubmit } = props;
  const [ruleName, setRuleName] = useState('');
  const [ruleKey, setRuleKey] = useState('');
  const [keyModifiedByUser, setKeyModifiedByUser] = useState(false);
  const [ruleType, setRuleType] = useState('CODE_SMELL');
  const [severity, setSeverity] = useState('MAJOR');
  const [message, setMessage] = useState('');
  const [ruleDescription, setRuleDescription] = useState('');
  const [charCount, setCharCount] = useState(0);

  const handleSubmit = () => {
    if (validateForm()) {
      onSubmit({
        ruleName,
        ruleKey,
        ruleType,
        severity,
        message,
        ruleDescription,
      });
    }
  };

  const validateForm = (): boolean => {
    return (
      ruleName.trim().length > 0 &&
      ruleKey.trim().length > 0 &&
      ruleType.length > 0 &&
      severity.length > 0 &&
      message.trim().length > 0 &&
      ruleDescription.trim().length >= MIN_CHAR_COUNT &&
      ruleDescription.trim().length <= MAX_CHAR_COUNT
    );
  };

  const typeOptions: LabelValueSelectOption[] = RULE_TYPES.map((type) => ({
    label: translate('issue.type', type),
    value: type,
  }));

  return (
    <Modal
      isOpen={isOpen}
      onOpenChange={onClose}
      title="Create AI Custom Rule"
      size={ModalSize.Wide}
      content={
        <div style={{ padding: '20px 0' }}>
          <div style={{ marginBottom: '20px', padding: '12px', backgroundColor: '#e6f4ff', borderRadius: '4px' }}>
            <Text>
              <strong><img src={SparklesSvg} alt="" width="16" height="16" style={{verticalAlign: 'middle', marginRight: '4px'}} />AI Rule Generation</strong>
              <br />
              Describe your coding standard in plain English. The AI will analyze your description
              and automatically generate the rule syntax.
            </Text>
          </div>

          <FormField
            ariaLabel="Rule Name"
            label="Rule Name"
            htmlFor="ai-rule-name"
            required
          >
            <InputField
              autoFocus
              id="ai-rule-name"
              onChange={({ currentTarget: { value } }: SyntheticEvent<HTMLInputElement>) => {
                setRuleName(value);
                if (!keyModifiedByUser) {
                  const generatedKey = latinize(value)
                    .replace(/[^A-Za-z0-9]/g, '_');
                  setRuleKey(generatedKey);
                }
              }}
              placeholder="e.g., Avoid Complex Methods"
              size="full"
              type="text"
              value={ruleName}
            />
          </FormField>

          <FormField
            ariaLabel="Rule Key"
            label="Rule Key"
            htmlFor="ai-rule-key"
            help="Auto-generated from rule name. This will be the unique identifier for your rule."
          >
            <InputField
              disabled
              id="ai-rule-key"
              placeholder="Auto-generated from rule name"
              size="full"
              type="text"
              value={ruleKey}
              style={{ backgroundColor: '#f5f5f5' }}
            />
          </FormField>

          <div style={{ display: 'flex', gap: '16px', marginBottom: '20px' }}>
            <div style={{ flex: 1 }}>
              <FormField
                ariaLabel="Rule Type"
                label="Rule Type"
                htmlFor="ai-rule-type"
                required
              >
                <Select
                  id="ai-rule-type"
                  data={typeOptions}
                  isNotClearable
                  onChange={(value) => setRuleType(value as string)}
                  value={ruleType}
                />
              </FormField>
            </div>

            <div style={{ flex: 1 }}>
              <FormField
                ariaLabel="Severity"
                label="Severity"
                htmlFor="ai-rule-severity"
                required
              >
                <SeveritySelect
                  ariaLabelledby="ai-rule-severity"
                  isDisabled={false}
                  onChange={({ value }: { value: string }) => setSeverity(value)}
                  severity={severity}
                />
              </FormField>
            </div>
          </div>

          <FormField
            ariaLabel="Message"
            label="Message"
            htmlFor="ai-rule-message"
            help="Short violation message shown in analysis results"
            required
          >
            <InputField
              id="ai-rule-message"
              onChange={({ currentTarget: { value } }: SyntheticEvent<HTMLInputElement>) =>
                setMessage(value)
              }
              placeholder="e.g., SOQL query inside a loop — a solution is possible to avoid governor limits"
              size="full"
              type="text"
              value={message}
            />
          </FormField>

          <FormField
            ariaLabel="Rule Prompt"
            label={`Describe your rule (${charCount}/${MAX_CHAR_COUNT} characters)`}
            htmlFor="ai-rule-description"
            help="Describe your coding standard in plain English. Be specific about what should be detected, where it happens in the code, and why it matters."
            required
          >
            <InputTextArea
              id="ai-rule-description"
              onChange={({ currentTarget: { value } }: SyntheticEvent<HTMLTextAreaElement>) => {
                setRuleDescription(value);
                setCharCount(value.length);
              }}
              placeholder="e.g., Find all flows that are not in active status"
              rows={6}
              size="full"
              value={ruleDescription}
            />
          </FormField>

          <div style={{ marginTop: '16px', padding: '12px', backgroundColor: '#f5f5f5', borderRadius: '4px' }}>
            <Text>
              <strong>Examples:</strong>
            </Text>
            <ul style={{ marginTop: '8px', paddingLeft: '20px' }}>
              {EXAMPLE_DESCRIPTIONS.map((example, index) => (
                <li key={index} style={{ marginBottom: '4px' }}>
                  "{example}"
                </li>
              ))}
            </ul>
          </div>
        </div>
      }
      primaryButton={
        <Button
          variety={ButtonVariety.Primary}
          isDisabled={!validateForm()}
          onClick={handleSubmit}
        >
          <img src={SparklesSvg} alt="" width="14" height="14" style={{verticalAlign: 'middle', marginRight: '4px'}} />Generate Rule
        </Button>
      }
      secondaryButton={
        <Button variety={ButtonVariety.Default} onClick={onClose}>
          ← Back
        </Button>
      }
    />
  );
}
