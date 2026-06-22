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

import { useState } from 'react';
import './ValidateXPathModal.css';
import { ValidationResult, splitXPath, validateAIXPath } from './aiRuleService';
import ChevronRightSvg from './icons/chevron-right.svg';
import InfoSvg from './icons/info.svg';
import ListChecksSvg from './icons/list-checks.svg';
import LoaderSvg from './icons/loader-anim.svg';
import TestXpathIcon from './icons/test-xpath.svg';
import XSvg from './icons/x.svg';

interface Props {
  xpath: string;
  language: string;
  onClose: () => void;
}

const MAX_CODE_SIZE = 50 * 1024; // 50 KB

const LANGUAGE_LABELS: { [key: string]: string } = {
  apex: 'Apex',
  vf: 'Visualforce',
  visualforce: 'Visualforce',
  sfmeta: 'Salesforce metadata',
  sfmetadata: 'Salesforce metadata',
};

export default function ValidateXPathModal(props: Readonly<Props>) {
  const { xpath, language, onClose } = props;
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ValidationResult | null>(null);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [showAst, setShowAst] = useState(false);

  const languageLabel = LANGUAGE_LABELS[language] ?? language.charAt(0).toUpperCase() + language.slice(1);
  const canValidate = code.trim().length > 0 && !loading;

  const codeLines = code.split('\n');

  const handleValidate = async () => {
    setLoading(true);
    setRequestError(null);
    setResult(null);
    setShowAst(false);
    try {
      const res = await validateAIXPath({ xpath, code, language });
      setResult(res);
    } catch (e) {
      setRequestError('Validation request failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const xpathLines = splitXPath(xpath);

  const renderResults = () => {
    if (requestError) {
      return (
        <div className="vxm-result vxm-result--error">
          <span className="vxm-result-text">{requestError}</span>
        </div>
      );
    }

    if (!result) {
      return null;
    }

    const hasErrors = result.errors && result.errors.length > 0;

    if (hasErrors) {
      return (
        <div className="vxm-result vxm-result--error">
          <span className="vxm-result-title">Expression error</span>
          <div className="vxm-error-list">
            {result.errors.map((err, i) => (
              <div key={i} className="vxm-error-row">
                {err.message}
                {(err.line > 0 || err.column > 0) && (
                  <span className="vxm-error-pos">
                    {' '}
                    Line {err.line}, Column {err.column}
                  </span>
                )}
              </div>
            ))}
          </div>
        </div>
      );
    }

    if (result.totalMatches === 0) {
      return (
        <div className="vxm-result vxm-result--info">
          <span className="vxm-result-title">No nodes matched.</span>
          <span className="vxm-result-text">
            The XPath ran without errors but returned 0 results in this sample. Try refining your
            expression or use a different code example.
          </span>
          {result.astPreview && renderAstToggle()}
        </div>
      );
    }

    return (
      <>
        <div className="vxm-result--success-banner">
          <div className="vxm-result--success-icon">✓</div>
          <span>
            {result.totalMatches} {result.totalMatches === 1 ? 'match' : 'matches'} found — XPath
            expression matched {result.totalMatches} {result.totalMatches === 1 ? 'node' : 'nodes'}{' '}
            in the sample code.
          </span>
        </div>
        <div className="vxm-match-list">
          {result.matches.map((m, i) => (
            <div key={i} className="vxm-match-row">
              <div className="vxm-match-info">
                <span className="vxm-match-node">
                  {(() => {
                    const rawName = m.nodeName || m.name || m.image || m.text;
                    if (rawName && rawName.startsWith('<')) {
                      // Extract node name from XML/tag start (e.g. "<CustomObject xmlns=..." -> "CustomObject")
                      const match = rawName.match(/^<([^\s>]+)/);
                      return match ? match[1] : rawName;
                    }
                    return rawName;
                  })()}
                </span>
                <span className="vxm-match-pos">
                  Line {m.line}, Column {m.column}
                </span>
              </div>
              <span className="vxm-match-index">Match #{i + 1}</span>
            </div>
          ))}
        </div>
        {result.astPreview && renderAstToggle()}
      </>
    );
  };

  const renderAstToggle = () => (
    <div className="vxm-ast">
      <button className="vxm-ast-toggle" type="button" onClick={() => setShowAst(!showAst)}>
        <img
          src={ChevronRightSvg}
          alt=""
          width="14"
          height="14"
          className={`vxm-ast-chevron${showAst ? ' vxm-ast-chevron--open' : ''}`}
        />
        {showAst ? 'Hide AST Preview' : 'Show AST Preview'}
      </button>
      {showAst && result?.astPreview && (
        <pre className="vxm-ast-tree">{result.astPreview}</pre>
      )}
    </div>
  );

  return (
    <div className="vxm-overlay" role="presentation" onClick={onClose}>
      <div
        className="vxm-modal"
        role="dialog"
        aria-modal="true"
        aria-label="Test XPath Expression"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="vxm-header">
          <div className="vxm-header-title">
            <div className="vxm-header-icon">
              <img src={TestXpathIcon} alt="" width="14" height="14" />
            </div>
            Test XPath Expression
          </div>
          <button className="vxm-close" type="button" onClick={onClose} aria-label="Close">
            <img src={XSvg} alt="" width="18" height="18" />
          </button>
        </div>

        {/* Body */}
        <div className="vxm-body">
          {/* XPath expression (read-only) */}
          <div className="vxm-field">
            <span className="vxm-label">
              <span className="vxm-label-number">1</span>
              XPath Expression
            </span>
            <div className="vxm-code vxm-code--readonly">
              <div className="vxm-code-numbers">
                {xpathLines.map((_, i) => (
                  <div key={i}>{i + 1}</div>
                ))}
              </div>
              <div className="vxm-code-content">
                {xpathLines.map((line, i) => (
                  <div key={i}>{line}</div>
                ))}
              </div>
            </div>
          </div>

          {/* Code example (editable) */}
          <div className="vxm-field">
            <span className="vxm-label">
              <span className="vxm-label-number">2</span>
              Code Example <span className="vxm-required">*</span>
            </span>
            <div className="vxm-code vxm-code--editable">
              <div className="vxm-code-numbers">
                {codeLines.map((_, i) => (
                  <div key={i}>{i + 1}</div>
                ))}
              </div>
              <div className="vxm-code-content">
                <textarea
                  className="vxm-code-textarea"
                  value={code}
                  placeholder={`Enter ${languageLabel} code to validate the XPath expression against...`}
                  onChange={(e) => setCode(e.target.value.slice(0, MAX_CODE_SIZE))}
                  spellCheck={false}
                />
              </div>
            </div>
            <div className="vxm-info">
              <img src={InfoSvg} alt="" width="13" height="13" className="vxm-info-icon" />
              <span>Max code size: 50 KB.</span>
            </div>
          </div>

          {/* Validation results */}
          {(result || requestError) && (
            <div className="vxm-field">
              <span className="vxm-label">VALIDATION RESULTS</span>
              {renderResults()}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="vxm-footer">
          <button className="vxm-btn vxm-btn--close" type="button" onClick={onClose}>
            Close
          </button>
          <button
            className="vxm-btn vxm-btn--primary"
            type="button"
            onClick={handleValidate}
            disabled={!canValidate}
          >
            {loading ? (
              <>
                <img src={LoaderSvg} alt="" width="15" height="15" className="vxm-spinner" />
                Validating...
              </>
            ) : result && !result.errors?.length ? (
              <>
                <img src={ListChecksSvg} alt="" width="15" height="15" className="vxm-btn-icon" />
                Validate
              </>
            ) : (
              <>
                <img src={ListChecksSvg} alt="" width="15" height="15" className="vxm-btn-icon" />
                Validate
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
