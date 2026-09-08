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

import { useEffect, useState } from 'react';
import './ReviewStep.css';
import ValidateXPathModal from './ValidateXPathModal';
import CheckLargeSvg from './icons/check-generated.svg';
import CheckSvg from './icons/check.svg';
import ChevronLeftSvg from './icons/chevron-left.svg';
import Code2Svg from './icons/code-2.svg';
import CopySvg from './icons/copy.svg';
import XOrangeSvg from './icons/x-orange.svg';
import InfoSvg from './icons/info.svg';
import ListChecksSvg from './icons/list-checks.svg';
import PencilSvg from './icons/pencil.svg';
import RotateCcwSvg from './icons/rotate-ccw.svg';
import SparklesSvg from './icons/sparkles.svg';
import AiSparkleSvg from './icons/sparkle-ai.svg';
import copied from './icons/copied.svg';
import severityBlocker from './icons/severity-blocker.svg';
import severityCritical from './icons/severity-critical.svg';
import severityInfo from './icons/severity-info.svg';
import severityMajor from './icons/severity-major.svg';
import severityMinor from './icons/severity-minor.svg';

import { RuleData, splitXPath } from './aiRuleService';
export type { RuleData };

interface Props {
  ruleData: RuleData;
  onBack: () => void;
  onRegenerate: () => void;
  onActivate: (xpath: string) => void;
  onValidate?: (xpath: string) => void;
  loading: boolean;
  hasError: boolean;
}

export default function ReviewStep(props: Readonly<Props>) {
  const { ruleData, onBack, onRegenerate, onActivate, onValidate, loading, hasError } = props;
  const [xpathValue, setXpathValue] = useState(ruleData.generatedXPath);
  const [isEditing, setIsEditing] = useState(false);
  const [isCopied, setIsCopied] = useState(false);
  const [showValidateModal, setShowValidateModal] = useState(false);

  const ruleLanguage = ruleData.language || 'apex';

  useEffect(() => {
    setXpathValue(ruleData.generatedXPath);
  }, [ruleData.generatedXPath]);

  const handleCopy = (e: React.MouseEvent<HTMLButtonElement>) => {
    navigator.clipboard.writeText(xpathValue);
    setIsCopied(true);
    e.currentTarget.blur();
    setTimeout(() => setIsCopied(false), 2000);
  };

  const formatRuleType = (type: string): string => {
    switch (type) {
      case 'BUG':
        return 'Bug';
      case 'VULNERABILITY':
        return 'Vulnerability';
      case 'CODE_SMELL':
        return 'Code Smell';
      default:
        return type;
    }
  };

  const formatSeverity = (severity: string): string => {
    return severity.charAt(0).toUpperCase() + severity.slice(1).toLowerCase();
  };

  const renderCodeEditor = () => {
    if (isEditing) {
      return (
        <div className="review-code-editor review-code-editor--editable">
          <textarea
            className="review-code-textarea"
            value={xpathValue}
            onChange={(e) => setXpathValue(e.target.value)}
            spellCheck={false}
          />
        </div>
      );
    }

    const lines = splitXPath(xpathValue);

    if (lines.length === 0 || (lines.length === 1 && !lines[0])) {
      return (
        <div className="review-code-editor">
          <span className="review-code-empty">
            {hasError ? 'XPath generation failed. Try regenerating.' : 'No XPath generated.'}
          </span>
        </div>
      );
    }

    return (
      <div className="review-code-editor">
        <div className="review-code-lines">
          <div className="review-code-numbers">
            {lines.map((_, i) => (
              <div key={i}>{i + 1}</div>
            ))}
          </div>
          <div className="review-code-content">
            {lines.map((line, i) => (
              <div key={i}>{line}</div>
            ))}
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="review-step">
      {/* Scrollable content */}
      <div className="review-scrollable">
        {/* Success/Error Banner */}
        {hasError ? (
          <div className="review-banner review-banner--error">
            <img src={XOrangeSvg} alt="" className="review-banner-icon" width="14" height="14" />
            <span className="review-banner-text">
              <strong>Rule generation failed.</strong> Check your description and click Regenerate to create the rule again.
            </span>
          </div>
        ) : (
          <div className="review-banner review-banner--success">
            <img src={CheckLargeSvg} alt="" className="review-banner-icon" width="15" height="15" />
            <span className="review-banner-text">
              <strong>Rule generated.</strong> Review the details below, edit the XPath if needed, then activate or save as draft.
            </span>
          </div>
        )}

        {/* Two-column layout */}
        <div className="review-panels">
          {/* Left: Rule details */}
          <div className="review-details">
            <div className="review-detail-table">
              <div className="review-detail-row">
                <span className="review-detail-label">Name</span>
                <span className="review-detail-value">{ruleData.ruleName}</span>
              </div>
              <div className="review-detail-row">
                <span className="review-detail-label">Key</span>
                <span className="review-detail-value"><code>{ruleData.ruleKey}</code></span>
              </div>
              <div className="review-detail-row">
                <span className="review-detail-label">Rule Type</span>
                <span className="review-detail-value">{formatRuleType(ruleData.ruleType)}</span>
              </div>
              <div className="review-detail-row">
                <span className="review-detail-label">Severity</span>
                <span className="review-detail-value">
                  {ruleData.severity === 'BLOCKER' && <img src={severityBlocker} alt="" width="12" height="12" className="review-severity-icon" />}
                  {ruleData.severity === 'CRITICAL' && <img src={severityCritical} alt="" width="12" height="12" className="review-severity-icon" />}
                  {ruleData.severity === 'MAJOR' && <img src={severityMajor} alt="" width="12" height="12" className="review-severity-icon" />}
                  {ruleData.severity === 'MINOR' && <img src={severityMinor} alt="" width="12" height="12" className="review-severity-icon" />}
                  {ruleData.severity === 'INFO' && <img src={severityInfo} alt="" width="12" height="12" className="review-severity-icon" />}
                  {formatSeverity(ruleData.severity)}
                </span>
              </div>
              <div className="review-detail-row">
                <span className="review-detail-label">Message</span>
                <span className="review-detail-value">{ruleData.message}</span>
              </div>
            </div>

            <div className="review-section">
              <div className="review-section-header">YOUR DESCRIPTION</div>
              <div className="review-section-content">{ruleData.description}</div>
            </div>

            {ruleData.aiSummary && (
              <div className="review-section">
                <div className="review-section-header">
                  <img src={SparklesSvg} alt="" width="14" height="14" className="review-section-header-icon" />
                  AI RULE SUMMARY
                </div>
                <div className="review-section-content review-section-content--ai">
                  {ruleData.aiSummary}
                </div>
              </div>
            )}
          </div>

          {/* Right: XPath editor */}
          <div className="review-xpath">
            <div className="review-xpath-header">
              <span className="review-xpath-title">
                <img src={Code2Svg} alt="" width="15" height="15" />
                GENERATED XPATH
              </span>
              <div className="review-xpath-actions">
                <button
                  className={`review-xpath-btn${isEditing ? ' review-xpath-btn--active' : ''}`}
                  onClick={() => setIsEditing(!isEditing)}
                  type="button"
                >
                  <img src={PencilSvg} alt="" width="12" height="12" />
                  {isEditing ? 'Done' : 'Edit'}
                </button>
                <button className={`review-xpath-btn${isCopied ? ' review-xpath-btn--copied' : ''}`} onClick={handleCopy} type="button">
                  {isCopied ? (
                    <>
                      <img src={copied} alt="" width="12" height="12" className="review-copied-icon" />
                      Copied
                    </>
                  ) : (
                    <>
                      <img src={CopySvg} alt="" width="12" height="12" />
                      Copy
                    </>
                  )}
                </button>
              </div>
            </div>

            {renderCodeEditor()}

            <div className="review-info-messages">
              <div className="review-info-msg">
                <img src={InfoSvg} alt="" width="13" height="13" className="review-info-msg-icon" />
                <span>This XPath expression is editable. Changes here will be traced back to your original description in the audit log.</span>
              </div>
              <div className="review-info-msg review-info-msg--warning">
                <img src={AiSparkleSvg} alt="" width="13" height="13" className="review-info-msg-icon" />
                <span>AI can make mistakes. Please review and verify generated rules before applying them.</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="review-footer">
        <div className="review-footer-left">
          <button className="review-btn review-btn--back" onClick={onBack} disabled={loading} type="button">
            <img src={ChevronLeftSvg} alt="" width="15" height="15" />
            Back
          </button>
          <button className="review-btn review-btn--regenerate" onClick={onRegenerate} disabled={loading} type="button">
            <img src={RotateCcwSvg} alt="" width="13" height="13" />
            Regenerate
          </button>
        </div>
        <div className="review-footer-right">
          <button
            className="review-btn review-btn--validate"
            onClick={() => {
              onValidate?.(xpathValue);
              setShowValidateModal(true);
            }}
            disabled={loading || hasError}
            type="button"
          >
            <img src={ListChecksSvg} alt="" width="15" height="15" className="review-btn-icon" />
            Validate
          </button>
          <button
            className="review-btn review-btn--primary"
            onClick={() => onActivate(xpathValue)}
            disabled={loading || hasError}
            type="button"
          >
            <img src={CheckSvg} alt="" width="15" height="15" className="review-btn-icon" />
            Create Rule
          </button>
        </div>
      </div>

      {showValidateModal && (
        <ValidateXPathModal
          xpath={xpathValue}
          language={ruleLanguage}
          onClose={() => setShowValidateModal(false)}
        />
      )}
    </div>
  );
}
