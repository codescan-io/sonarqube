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

import { useEffect, useRef, useState } from 'react';
import './GenerateStep.css';
import Check from './icons/check.svg';
import CheckLoadingSvg from './icons/check-loading.svg';
import SparklesLoadingSvg from './icons/sparkles-loading.svg';

interface Props {
  ruleName: string;
  onComplete: () => void;
  hasError: boolean;
}

const GENERATION_STEPS = [
  'Analyzing natural language description...',
  'Identifying target artifact and context...',
  'Extracting rule constraints and thresholds...',
  'Generating XPath expression...',
  'Validating rule syntax...',
];

const STEP_DURATION = 1500; // ms per step

export default function GenerateStep(props: Readonly<Props>) {
  const { ruleName, onComplete, hasError } = props;
  const [activeStepIndex, setActiveStepIndex] = useState(0);
  const [isComplete, setIsComplete] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const completedRef = useRef(false);

  useEffect(() => {
    // Reset state on mount
    setActiveStepIndex(0);
    setIsComplete(false);
    completedRef.current = false;

    timerRef.current = setInterval(() => {
      setActiveStepIndex((prev) => {
        const next = prev + 1;
        if (next >= GENERATION_STEPS.length) {
          if (timerRef.current) {
            clearInterval(timerRef.current);
          }
          // Mark as complete after last step
          setTimeout(() => {
            setIsComplete(true);
          }, STEP_DURATION);
          return prev;
        }
        return next;
      });
    }, STEP_DURATION);

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, []);

  // Handle transition to review after success display
  useEffect(() => {
    if (isComplete && !completedRef.current) {
      completedRef.current = true;
      const timeout = setTimeout(() => {
        onComplete();
      }, 2000);
      return () => clearTimeout(timeout);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isComplete]);

  // If API returned error, still show the animation, then transition
  useEffect(() => {
    if (hasError && activeStepIndex >= GENERATION_STEPS.length - 1) {
      const timeout = setTimeout(() => {
        if (!completedRef.current) {
          completedRef.current = true;
          onComplete();
        }
      }, 2000);
      return () => clearTimeout(timeout);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasError, activeStepIndex]);

  const getStepStatus = (index: number): 'completed' | 'active' | 'pending' => {
    if (isComplete) return 'completed';
    if (index < activeStepIndex) return 'completed';
    if (index === activeStepIndex) return 'active';
    return 'pending';
  };

  const renderStepIcon = (status: 'completed' | 'active' | 'pending') => {
    if (status === 'completed') {
      return (
        <div className="generate-step-item-icon generate-step-item-icon--completed">
          <img src={CheckLoadingSvg} alt="" width="10" height="10" />
        </div>
      );
    }
    if (status === 'active') {
      return (
        <div className="generate-step-item-icon generate-step-item-icon--active">
          <div className="generate-step-loader" />
        </div>
      );
    }
    return (
      <div className="generate-step-item-icon generate-step-item-icon--pending">
        <div className="generate-step-pending-dot" />
      </div>
    );
  };

  return (
    <div className="generate-step">
      <div className={`generate-step-icon${isComplete ? ' generate-step-icon--success' : ' generate-step-icon--processing'}`}>
        {isComplete
          ? <img src={Check} alt="" width="26" height="26" />
          : <img src={SparklesLoadingSvg} alt="" width="27" height="27" />
        }
      </div>

      <h2 className="generate-step-title">
        {isComplete
          ? 'Rule Generated Successfully'
          : `Generating Rule "${ruleName}"...`}
      </h2>

      <p className="generate-step-subtitle">
        {isComplete
          ? 'Your rule is ready for review. We\'ll redirect you now.'
          : 'AI is analyzing your description and creating the XPath expression.'}
      </p>

      <div className="generate-step-steps">
        {GENERATION_STEPS.map((step, index) => {
          const status = getStepStatus(index);
          return (
            <div key={index} className="generate-step-row">
              <div className={`generate-step-item generate-step-item--${status}`}>
                {renderStepIcon(status)}
                <span className={`generate-step-item-text generate-step-item-text--${status}`}>
                  {step}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
