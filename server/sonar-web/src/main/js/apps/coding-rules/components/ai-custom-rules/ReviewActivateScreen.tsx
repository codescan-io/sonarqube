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
import { Button, ButtonVariety, Modal, ModalSize, Text } from '@sonarsource/echoes-react';
import { useEffect, useState } from 'react';
import { FlagMessage, FormField, InputTextArea } from '~design-system';
import './ReviewActivateScreen.css';
import SparklesSvg from './icons/Sparkles-2.svg';

export interface RuleData {
  ruleName: string;
  ruleKey: string;
  ruleType: string;
  severity: string;
  message: string;
  description: string;
  generatedXPath: string;
  aiSummary: string;
}

interface Props {
  isOpen: boolean;
  ruleData: RuleData;
  onBack: () => void;
  onRegenerate: () => void;
  onCreate: () => void;
  loading?: boolean;
  regenerating?: boolean;
}

export default function ReviewActivateScreen(props: Readonly<Props>) {
  const { isOpen, ruleData, onBack, onRegenerate, onCreate, loading, regenerating } = props;
  const [xpathValue, setXpathValue] = useState(ruleData.generatedXPath);
  const [isEditing, setIsEditing] = useState(false);

  // Sync XPath value when ruleData changes (e.g., after regeneration)
  useEffect(() => {
    setXpathValue(ruleData.generatedXPath);
  }, [ruleData.generatedXPath]);

  const handleCopy = () => {
    navigator.clipboard.writeText(xpathValue);
  };

  return (
    <Modal
      isOpen={isOpen}
      onOpenChange={onBack}
      title="Review and Activate Rule"
      size={ModalSize.Wide}
      content={
        <div className="review-content">
          <FlagMessage variant="success">
            ✓ Rule generated successfully! Review the details below, edit the XPath if needed, then create the rule or regenerate.
          </FlagMessage>

          <div className="review-panels">
            <div className="details-panel">
              <h3>Rule Details</h3>
              <div className="detail-row">
                <span className="label">Name:</span>
                <span>{ruleData.ruleName}</span>
              </div>
              <div className="detail-row">
                <span className="label">Key:</span>
                <code>{ruleData.ruleKey}</code>
              </div>
              <div className="detail-row">
                <span className="label">Type:</span>
                <span>{ruleData.ruleType}</span>
              </div>
              <div className="detail-row">
                <span className="label">Severity:</span>
                <span>{ruleData.severity}</span>
              </div>
              <div className="detail-row">
                <span className="label">Message:</span>
                <span>{ruleData.message}</span>
              </div>

              <h3 style={{ marginTop: '20px' }}>Your Prompt</h3>
              <Text>{ruleData.description}</Text>

              <h3 style={{ marginTop: '20px' }}>Rule Description</h3>
              <Text>{ruleData.aiSummary}</Text>
            </div>

            <div className="xpath-panel">
              <div className="xpath-header">
                <h3><img src={SparklesSvg} alt="" width="16" height="16" style={{verticalAlign: 'middle', marginRight: '4px'}} />Generated XPath</h3>
                <div>
                  <Button
                    variety={ButtonVariety.Default}
                    onClick={() => setIsEditing(!isEditing)}
                  >
                    {isEditing ? 'Done Editing' : 'Edit'}
                  </Button>
                  <Button
                    variety={ButtonVariety.Default}
                    onClick={handleCopy}
                    style={{ marginLeft: '8px' }}
                  >
                    Copy
                  </Button>
                </div>
              </div>

              <FormField ariaLabel="XPath Expression" label="">
                <InputTextArea
                  value={xpathValue}
                  onChange={(e: any) => setXpathValue(e.currentTarget.value)}
                  readOnly={!isEditing}
                  rows={12}
                  size="full"
                  style={{ fontFamily: 'monospace', fontSize: '12px' }}
                />
              </FormField>

              {isEditing && (
                <FlagMessage variant="info">
                  This XPath expression is editable. Changes will be saved with your rule.
                </FlagMessage>
              )}

              <FlagMessage variant="warning">
                ⚠ AI can make mistakes. Please review and verify generated rules before applying them.
              </FlagMessage>
            </div>
          </div>
        </div>
      }
      primaryButton={
        <Button
          variety={ButtonVariety.Primary}
          onClick={onCreate}
          isDisabled={loading || regenerating}
        >
          Create Rule
        </Button>
      }
      secondaryButton={
        <>
          <Button
            variety={ButtonVariety.Default}
            onClick={onRegenerate}
            isDisabled={loading || regenerating}
          >
            {regenerating ? 'Regenerating...' : '↻ Regenerate'}
          </Button>
          <Button variety={ButtonVariety.Default} onClick={onBack} isDisabled={regenerating}>
            ← Back
          </Button>
        </>
      }
    />
  );
}
