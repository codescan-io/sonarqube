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

import { Select } from '@sonarsource/echoes-react';
import { useState } from 'react';
import IssueTypeIcon from '../../../../components/icon-mappers/IssueTypeIcon';
import SoftwareImpactSeverityIcon from '../../../../components/icon-mappers/SoftwareImpactSeverityIcon';
import { latinize } from '../../../../helpers/strings';
import './DefineStep.css';
import AlertCircleSvg from './icons/alert-circle.svg';
import ChevronLeftSvg from './icons/chevron-left.svg';
import SparklesSvg from './icons/sparkles.svg';

export interface AIRuleFormValues {
  ruleName: string;
  ruleKey: string;
  ruleType: string;
  severity: string;
  message: string;
  ruleDescription: string;
}

interface Props {
  onSubmit: (values: AIRuleFormValues) => void;
  onBack: () => void;
  initialValues?: AIRuleFormValues | null;
  /** Message from the api's pre-generation rule key validation, shown against the key field. */
  serverError?: string;
}

const MAX_CHAR_COUNT = 5000;
const MIN_CHAR_COUNT = 20;

const EXAMPLE_DESCRIPTIONS = [
  'Custom objects must include a description.',
  'All custom report types must include a description.',
  'Custom settings must not store sensitive credentials.',
];

const EXAMPLE_FULL_TEXT = [
  'Custom objects must include a description.',
  'All custom report types must include a description.',
  'Custom settings must not store sensitive credentials.',
];

const RULE_TYPES = [
  { value: 'BUG', label: 'Bug' },
  { value: 'VULNERABILITY', label: 'Vulnerability' },
  { value: 'CODE_SMELL', label: 'Code Smell' },
  { value: 'SECURITY_HOTSPOT', label: 'Security Hotspot' },
];

const SEVERITIES = [
  { value: 'BLOCKER', label: 'Blocker' },
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'MAJOR', label: 'Major (recommended)' },
  { value: 'MINOR', label: 'Minor' },
  { value: 'INFO', label: 'Info' },
];

const SparkleIcon = () => (
  <img src={SparklesSvg} alt="" className="define-step-banner-icon" width="21" height="21" />
);

export default function DefineStep(props: Readonly<Props>) {
  const { onSubmit, onBack, initialValues, serverError } = props;

  const [ruleName, setRuleName] = useState(initialValues?.ruleName || '');
  const [ruleKey, setRuleKey] = useState(initialValues?.ruleKey || '');
  const [keyModifiedByUser] = useState(false);
  const [ruleType, setRuleType] = useState(initialValues?.ruleType || 'BUG');
  const [severity, setSeverity] = useState(initialValues?.severity || 'MAJOR');
  const [message, setMessage] = useState(initialValues?.message || '');
  const [ruleDescription, setRuleDescription] = useState(initialValues?.ruleDescription || '');
  const [submitted, setSubmitted] = useState(false);

  const charCount = ruleDescription.length;

  const generateKey = (name: string): string => {
    return latinize(name)
      .replace(/[^a-zA-Z0-9\s_-]/g, '')
      .replace(/\s+/g, '_')
      .replace(/_+/g, '_')
      .substring(0, 50);
  };

  const handleNameChange = (value: string) => {
    setRuleName(value);
    if (!keyModifiedByUser) {
      setRuleKey(generateKey(value));
    }
  };

  const handleExampleClick = (index: number) => {
    setRuleDescription(EXAMPLE_FULL_TEXT[index]);
  };

  const isValid = (): boolean => {
    return (
      ruleName.trim().length > 0 &&
      ruleKey.trim().length > 0 &&
      ruleType.length > 0 &&
      severity.length > 0 &&
      ruleDescription.trim().length >= MIN_CHAR_COUNT &&
      ruleDescription.trim().length <= MAX_CHAR_COUNT
    );
  };

  const handleSubmit = () => {
    setSubmitted(true);
    if (isValid()) {
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

  return (
    <div className="define-step">
      {/* Scrollable content area */}
      <div className="define-step-scrollable">
        {/* Info Banner */}
        <div className="define-step-banner">
          <SparkleIcon />
          <div className="define-step-banner-text">
            <strong>AI Rule Generation:</strong> Describe your coding standard in plain English. The
            AI will analyze your description and automatically generate the corresponding XPath rule
            for you to review.
          </div>
        </div>

        {/* Form */}
        <div className="define-step-form">
          {/* Row 1: Name + Key */}
          <div className="define-step-row">
            <div className="define-step-field define-step-field--wide">
              <div className="define-step-label">
                <span className="define-step-label-text">
                  Rule Name<span className="define-step-label-required">*</span>
                </span>
              </div>
              <input
                className={`define-step-input ${submitted && !ruleName.trim() ? 'define-step-input--error' : ''}`}
                type="text"
                placeholder="No SOQL in Loops"
                value={ruleName}
                onChange={(e) => handleNameChange(e.target.value)}
                autoFocus
              />
              {submitted && !ruleName.trim() && (
                <div className="define-step-error">
                  <img src={AlertCircleSvg} alt="" className="define-step-error-icon" />
                  Rule name is required.
                </div>
              )}
            </div>
            <div className="define-step-field define-step-field--narrow">
              <div className="define-step-label">
                <span className="define-step-label-text">
                  Rule Key<span className="define-step-label-required">*</span>
                </span>
                <span className="define-step-label-help">Auto-generated from name</span>
              </div>
              <input
                className={`define-step-input define-step-input--disabled ${submitted && !ruleKey.trim() ? 'define-step-input--error' : ''}`}
                type="text"
                placeholder="No_SOQL_in_Loops"
                value={ruleKey}
                disabled
              />
              {submitted && !ruleKey.trim() && (
                <div className="define-step-error">
                  <img src={AlertCircleSvg} alt="" className="define-step-error-icon" />
                  Rule key is required.
                </div>
              )}
              {serverError && (
                <div className="define-step-error">
                  <img src={AlertCircleSvg} alt="" className="define-step-error-icon" />
                  {serverError}
                </div>
              )}
            </div>
          </div>

          {/* Row 2: Type + Severity */}
          <div className="define-step-row">
            <div className="define-step-field">
              <div className="define-step-label">
                <span className="define-step-label-text">Rule Type</span>
              </div>
              <Select
                id="ai-rule-type-select"
                isNotClearable
                isSearchable={false}
                onChange={(value) => value && setRuleType(value)}
                data={RULE_TYPES.map((type) => ({
                  label: type.label,
                  value: type.value,
                  prefix: <IssueTypeIcon type={type.value} />,
                }))}
                value={ruleType}
                valueIcon={<IssueTypeIcon type={ruleType} />}
              />
            </div>
            <div className="define-step-field">
              <div className="define-step-label">
                <span className="define-step-label-text">Severity</span>
              </div>
              <Select
                id="ai-rule-severity-select"
                isNotClearable
                isSearchable={false}
                onChange={(value) => value && setSeverity(value)}
                data={SEVERITIES.map((sev) => ({
                  label: sev.label,
                  value: sev.value,
                  prefix: <SoftwareImpactSeverityIcon severity={sev.value} aria-hidden />,
                }))}
                value={severity}
                valueIcon={<SoftwareImpactSeverityIcon severity={severity} aria-hidden />}
              />
            </div>
          </div>

          {/* Row 3: Message */}
          <div className="define-step-field-full">
            <div className="define-step-label">
              <span className="define-step-label-text">Message</span>
              <span className="define-step-label-help">Shown in violation reports</span>
            </div>
            <input
              className="define-step-input"
              type="text"
              placeholder="SOQL query inside a loop — move it outside to avoid governor limits."
              value={message}
              onChange={(e) => setMessage(e.target.value)}
            />
          </div>

          {/* Row 4: Description */}
          <div className="define-step-field-full">
            <div className="define-step-label">
              <span className="define-step-label-text">
                Rule Description<span className="define-step-label-required">*</span>
              </span>
              <span className="define-step-label-help">{charCount} characters</span>
            </div>
            <textarea
              className={`define-step-textarea ${submitted && ruleDescription.trim().length < MIN_CHAR_COUNT ? 'define-step-textarea--error' : ''}`}
              placeholder="Describe your coding standard in plain English. Be specific about what should or should not happen in the code."
              value={ruleDescription}
              onChange={(e) => setRuleDescription(e.target.value)}
              rows={5}
            />
            {submitted && ruleDescription.trim().length < MIN_CHAR_COUNT && (
              <div className="define-step-error">
                <img src={AlertCircleSvg} alt="" className="define-step-error-icon" />
                Rule description is required.
              </div>
            )}
          </div>

          {/* Examples */}
          <div className="define-step-examples">
            <div className="define-step-examples-label">Try an example:</div>
            <div className="define-step-examples-list">
              {EXAMPLE_DESCRIPTIONS.map((example, index) => (
                <button
                  key={index}
                  className="define-step-example-chip"
                  onClick={() => handleExampleClick(index)}
                  type="button"
                >
                  "{example}"
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Fixed footer - stays at bottom */}
      <div className="define-step-footer-wrapper">
        <div className="define-step-divider" />
        <div className="define-step-footer">
          <button className="define-step-btn-back" onClick={onBack} type="button">
            <img src={ChevronLeftSvg} alt="" className="define-step-btn-back-icon" />
            Back
          </button>
          <button
            className="define-step-btn-generate"
            onClick={handleSubmit}
            disabled={submitted && !isValid()}
            type="button"
          >
            <img
              src={SparklesSvg}
              alt=""
              className="define-step-btn-generate-icon"
              width="16"
              height="16"
            />
            Generate Rule
          </button>
        </div>
      </div>
    </div>
  );
}
