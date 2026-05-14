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

import { Modal, Text } from '@sonarsource/echoes-react';
import { useEffect, useState } from 'react';
import { Spinner } from '~design-system';
import './GenerationProgressModal.css';
import SparklesSvg from './icons/Sparkles-2.svg';
import CheckSvg from './icons/Check.svg';

interface Props {
  isOpen: boolean;
  ruleName: string;
  onComplete?: () => void;
}

interface ProgressStep {
  label: string;
  status: 'pending' | 'active' | 'completed';
}

const INITIAL_STEPS: ProgressStep[] = [
  { label: 'Analyzing natural language description...', status: 'pending' },
  { label: 'Identifying target artifact and context...', status: 'pending' },
  { label: 'Extracting rule constraints and thresholds...', status: 'pending' },
  { label: 'Generating XPath expression...', status: 'pending' },
  { label: 'Validating rule syntax...', status: 'pending' },
];

export default function GenerationProgressModal(props: Readonly<Props>) {
  const { isOpen, ruleName, onComplete } = props;
  const [progress, setProgress] = useState(0);
  const [steps, setSteps] = useState<ProgressStep[]>(INITIAL_STEPS);

  useEffect(() => {
    if (!isOpen) return;

    // Reset state
    setProgress(0);
    setSteps(INITIAL_STEPS);

    const totalSteps = INITIAL_STEPS.length;
    let currentStepIndex = 0;

    const progressInterval = setInterval(() => {
      setProgress((prev) => {
        const newProgress = Math.min(prev + 2, 100);
        const stepProgress = (newProgress / 100) * totalSteps;
        const newStepIndex = Math.floor(stepProgress);

        if (newStepIndex !== currentStepIndex && newStepIndex < totalSteps) {
          currentStepIndex = newStepIndex;
          setSteps((prevSteps) =>
            prevSteps.map((step, index) => ({
              ...step,
              status:
                index < newStepIndex
                  ? 'completed'
                  : index === newStepIndex
                  ? 'active'
                  : 'pending',
            }))
          );
        }

        if (newProgress >= 100) {
          clearInterval(progressInterval);
          setSteps((prevSteps) =>
            prevSteps.map((step) => ({ ...step, status: 'completed' as const }))
          );
          setTimeout(() => {
            onComplete?.();
          }, 500);
        }

        return newProgress;
      });
    }, 100);

    return () => clearInterval(progressInterval);
  }, [isOpen, onComplete]);

  const renderStepIcon = (status: 'pending' | 'active' | 'completed') => {
    if (status === 'completed') {
      return <img src={CheckSvg} alt="" width="16" height="16" />;
    }
    if (status === 'active') {
      return <Spinner />;
    }
    return <div className="pending-dot" />;
  };

  return (
    <Modal
      isOpen={isOpen}
      onOpenChange={() => {}}
      title=""
      content={
        <div className="progress-content">
          <div className="progress-header">
            <div className="ai-icon-wrapper">
              <img src={SparklesSvg} alt="" width="32" height="32" />
            </div>
            <Text>
              <strong>Generating Rule "{ruleName}"...</strong>
            </Text>
            <Text className="progress-subtitle">
              AI is analyzing your description and creating the XPath expression.
            </Text>
          </div>

          <div className="progress-bar-wrapper">
            <div className="progress-bar">
              <div
                className="progress-bar-fill"
                style={{
                  width: `${progress}%`,
                  background:
                    progress < 50
                      ? '#108ee9'
                      : 'linear-gradient(to right, #108ee9, #87d068)',
                }}
              />
            </div>
            <div className="progress-percentage">{progress}%</div>
          </div>

          <div className="progress-steps">
            {steps.map((step, index) => (
              <div key={index} className={`progress-step step-${step.status}`}>
                <div className="step-icon">{renderStepIcon(step.status)}</div>
                <span className="step-label">{step.label}</span>
              </div>
            ))}
          </div>

          {progress < 100 && (
            <div className="progress-footer">
              <Text className="text-muted">This usually takes 5-15 seconds...</Text>
            </div>
          )}
        </div>
      }
    />
  );
}
