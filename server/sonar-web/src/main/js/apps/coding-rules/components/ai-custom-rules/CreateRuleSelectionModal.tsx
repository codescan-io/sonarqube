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
import './CreateRuleSelectionModal.css';
import CheckSvg from './icons/check.svg';
import ChevronRightSvg from './icons/chevron-right.svg';
import Code2Svg from './icons/code-2.svg';
import SparklesSvg from './icons/sparkles-slate.svg';

interface Props {
  aiEnabled: boolean;
  isOpen: boolean;
  onClose: () => void;
  onSelectAI: () => void;
  onSelectXPath: () => void;
}

type CardSelection = 'ai' | 'xpath' | null;

export default function CreateRuleSelectionModal(props: Readonly<Props>) {
  const { aiEnabled, isOpen, onClose, onSelectAI, onSelectXPath } = props;
  const [selected, setSelected] = useState<CardSelection>(null);
  const [showAiDisabledMessage, setShowAiDisabledMessage] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setSelected(null);
      setShowAiDisabledMessage(false);
    }
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  const handleCardClick = (card: CardSelection) => {
    setSelected(card);
    if (card === 'ai') {
      if (!aiEnabled) {
        setShowAiDisabledMessage(true);
        return;
      }
      onSelectAI();
    } else if (card === 'xpath') {
      setShowAiDisabledMessage(false);
      onSelectXPath();
    }
  };

  return (
    <div className="create-rule-modal-overlay" onClick={onClose}>
      <div className="create-rule-modal" onClick={(e) => e.stopPropagation()}>
        <div className="create-rule-modal-header">
          <h2 className="create-rule-modal-title">Create Custom Rule</h2>
          <button className="create-rule-modal-close" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>

        <div className="create-rule-modal-body">
          <p className="modal-description">
            Choose how you'd like to define your custom rule. You can describe it in plain English
            using AI, or write an XPath expression directly.
          </p>

          <div className="selection-cards">
            {/* AI Natural Language Option */}
            <div
              className={`selection-card ai-card${selected === 'ai' ? ' selected' : ''}`}
              onClick={() => handleCardClick('ai')}
            >
              <div className="card-header">
                <div className="card-icon-wrapper">
                  <img className="card-icon" src={SparklesSvg} alt="" />
                </div>
                <span className="recommended-badge">Recommended</span>
              </div>
              <h3>AI Natural Language Rule</h3>
              <p className="card-description">
                Describe your coding standard in plain English and let AI translate it into
                an executable CodeScan rule.
              </p>
              <ul className="feature-list">
                <li><img className="check-icon" src={CheckSvg} alt="" />Describe rules in natural language</li>
                <li><img className="check-icon" src={CheckSvg} alt="" />AI-generated and editable</li>
                <li><img className="check-icon" src={CheckSvg} alt="" />Faster custom rule creation</li>
              </ul>
              <span className="get-started-link">
                Get started <img className="chevron-icon" src={ChevronRightSvg} alt="" />
              </span>
            </div>

            {/* XPath Expression Option */}
            <div
              className={`selection-card xpath-card${selected === 'xpath' ? ' selected' : ''}`}
              onClick={() => handleCardClick('xpath')}
            >
              <div className="card-header">
                <div className="card-icon-wrapper">
                  <img className="card-icon" src={Code2Svg} alt="" />
                </div>
              </div>
              <h3>XPath Expression Rule</h3>
              <p className="card-description">
                Write your rule directly using an XPath expression. Ideal for teams
                with existing rule definitions or advanced users who prefer full control.
              </p>
              <ul className="feature-list">
                <li><img className="check-icon" src={CheckSvg} alt="" />Full XPath control</li>
                <li><img className="check-icon" src={CheckSvg} alt="" />Existing expressions supported</li>
                <li><img className="check-icon" src={CheckSvg} alt="" />Manual precision</li>
              </ul>
              <span className="get-started-link">
                Get started <img className="chevron-icon" src={ChevronRightSvg} alt="" />
              </span>
            </div>
          </div>

          {showAiDisabledMessage && (
            <div className="ai-disabled-message" role="alert">
              Enable the AI custom rules toggle in Organization settings to use this option.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
