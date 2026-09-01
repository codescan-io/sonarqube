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
import { addGlobalErrorMessage } from '~design-system';
import { createRule } from '../../../../api/rules';
import { translate, translateWithParameters } from '../../../../helpers/l10n';
import { RuleDetails } from '../../../../types/types';
import { generateAIXPath, getAiRuleQuota, parseCodescanErrorMessage } from './aiRuleService';
import './AIRuleWizard.css';
import DefineStep, { AIRuleFormValues } from './DefineStep';
import GenerateStep from './GenerateStep';
import CheckSvg from './icons/check.svg';
import ChevronRightSvg from './icons/chevron-right.svg';
import XSvg from './icons/x.svg';
import ReviewStep, { RuleData } from './ReviewStep';

type WizardStep = 'define' | 'generate' | 'review';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onBack?: () => void;
  organization: string;
  templateRule: RuleDetails;
}

const CheckIcon = () => (
  <img src={CheckSvg} alt="" width="14" height="14" />
);

export default function AIRuleWizard(props: Readonly<Props>) {
  const { isOpen, onClose, onBack, organization, templateRule } = props;
  const [currentStep, setCurrentStep] = React.useState<WizardStep>('define');
  const [formValues, setFormValues] = React.useState<AIRuleFormValues | null>(null);
  const [ruleData, setRuleData] = React.useState<RuleData | null>(null);
  const [generationError, setGenerationError] = React.useState(false);
  const [defineError, setDefineError] = React.useState<string | undefined>(undefined);
  const [loading, setLoading] = React.useState(false);

  const handleClose = () => {
    setCurrentStep('define');
    setRuleData(null);
    setGenerationError(false);
    setLoading(false);
    onClose();
  };

  if (!isOpen) {
    return null;
  }

  const getBreadcrumbText = (): string => {
    switch (currentStep) {
      case 'define':
        return 'Define AI Rule';
      case 'generate':
        return 'Generating Rule...';
      case 'review':
        return 'Review & Activate';
    }
  };

  const getStepStatus = (step: WizardStep): 'active' | 'completed' | 'inactive' => {
    const stepOrder: WizardStep[] = ['define', 'generate', 'review'];
    const currentIndex = stepOrder.indexOf(currentStep);
    const stepIndex = stepOrder.indexOf(step);

    if (stepIndex < currentIndex) return 'completed';
    if (stepIndex === currentIndex) return 'active';
    return 'inactive';
  };

  const handleDefineSubmit = async (values: AIRuleFormValues) => {
    setFormValues(values);
    setDefineError(undefined);

    try {
      if (!organization) {
        throw new Error('Organization is required to create a custom rule');
      }

      // Short-circuit on credit shortage — backend would 400 anyway; failing fast here
      // avoids spending a partial round-trip and gives the user a clearer message.
      const quota = await getAiRuleQuota(organization);
      if (!quota.moduleLicensed) {
        addGlobalErrorMessage(translate('ai_custom_rules.module_not_licensed'));
        return;
      }
      if (quota.remainingCredits < quota.costPerGenerate) {
        addGlobalErrorMessage(translateWithParameters(
          'ai_custom_rules.insufficient_credits',
          String(quota.costPerGenerate),
          String(quota.remainingCredits)));
        return;
      }

      setCurrentStep('generate');
      setGenerationError(false);

      const response = await generateAIXPath({
        description: values.ruleDescription,
        organization,
        ruleKey: values.ruleKey,
        templateKey: templateRule.key,
      });

      // Check if AI rejected the input (invalid/unrelated description)
      const isInvalidInput = response.status === 'INVALID_INPUT' || !response.generatedXPath;

      setRuleData({
        ruleName: values.ruleName,
        ruleKey: values.ruleKey,
        ruleType: values.ruleType,
        severity: values.severity,
        message: values.message,
        description: values.ruleDescription,
        generatedXPath: isInvalidInput ? '' : response.generatedXPath,
        aiSummary: response.aiSummary || '',
        language: templateRule.lang || templateRule.repo,
      });
      setGenerationError(isInvalidInput);
    } catch (error) {
      // The api validates the rule key before spending credits, so a rejection here is a
      // correctable input error, not a generation failure — keep the user on the Define step
      // with the message rather than showing them a failed generation.
      const message = await parseCodescanErrorMessage(error);
      if (message !== undefined) {
        setCurrentStep('define');
        setDefineError(message);
        return;
      }
      setGenerationError(true);
      setRuleData({
        ruleName: values.ruleName,
        ruleKey: values.ruleKey,
        ruleType: values.ruleType,
        severity: values.severity,
        message: values.message,
        description: values.ruleDescription,
        generatedXPath: '',
        aiSummary: '',
        language: templateRule.lang || templateRule.repo,
      });
    }
  };

  const handleGenerationComplete = () => {
    setCurrentStep('review');
  };

  const handleReviewBack = () => {
    setCurrentStep('define');
  };

  const handleRegenerate = async () => {
    if (!formValues) return;

    try {
      if (!organization) {
        throw new Error('Organization is required to create a custom rule');
      }

      const quota = await getAiRuleQuota(organization);
      if (!quota.moduleLicensed) {
        addGlobalErrorMessage(translate('ai_custom_rules.module_not_licensed'));
        return;
      }
      if (quota.remainingCredits < quota.costPerRegenerate) {
        addGlobalErrorMessage(translateWithParameters(
          'ai_custom_rules.insufficient_credits',
          String(quota.costPerRegenerate),
          String(quota.remainingCredits)));
        return;
      }

      setCurrentStep('generate');
      setGenerationError(false);

      const previousXPath = ruleData?.generatedXPath;
      const response = await generateAIXPath({
        description: previousXPath
          ? `${formValues.ruleDescription}\n\n[REGENERATE: The previous XPath was rejected as inaccurate. Generate a different one. Previous: ${previousXPath}]`
          : formValues.ruleDescription,
        organization,
        ruleKey: formValues.ruleKey,
        templateKey: templateRule.key,
        regenerate: true,
      });

      setRuleData({
        ruleName: formValues.ruleName,
        ruleKey: formValues.ruleKey,
        ruleType: formValues.ruleType,
        severity: formValues.severity,
        message: formValues.message,
        description: formValues.ruleDescription,
        generatedXPath: response.generatedXPath || '// XPath not generated',
        aiSummary: response.aiSummary || 'XPath generated from natural language description.',
        language: templateRule.lang || templateRule.repo,
      });
      setGenerationError(false);
    } catch (error) {
      const message = await parseCodescanErrorMessage(error);
      if (message !== undefined) {
        addGlobalErrorMessage(message);
      }
      setGenerationError(true);
    }
  };

  const handleActivateRule = async (xpathValue: string) => {
    if (!formValues || !ruleData) return;
    setLoading(true);
    try {
      if (!organization) {
        throw new Error('Organization is required to create a custom rule');
      }

      const markdownDescription = ruleData.aiSummary?.trim()
        ? ruleData.aiSummary
        : `${formValues.ruleName}: ${formValues.message}`;

      await createRule({
        organization,
        aiGenerated: true,
        key: `${templateRule.repo}:${formValues.ruleKey}`,
        templateKey: templateRule.key,
        name: formValues.ruleName,
        markdownDescription,
        severity: formValues.severity,
        type: formValues.ruleType as any,
        impacts: [],
        parameters: [
          { key: 'xpath', defaultValue: xpathValue },
          { key: 'message', defaultValue: formValues.message },
        ],
      });

      onClose();
      window.location.reload();
    } catch (error: any) {
      const message =
        error?.data?.message ||
        error?.data?.errors?.map((e: any) => e.msg).join(', ') ||
        error?.message ||
        JSON.stringify(error);
      alert(`Failed to create rule: ${message}`);
    } finally {
      setLoading(false);
    }
  };

  const renderStepNumber = (step: WizardStep, number: number) => {
    const status = getStepStatus(step);
    if (status === 'completed') {
      return (
        <span className="ai-wizard-step-check">
          <CheckIcon />
        </span>
      );
    }
    return (
      <span className={`ai-wizard-step-number ai-wizard-step-number--${status}`}>{number}</span>
    );
  };

  const renderStepper = () => (
    <div className="ai-wizard-stepper">
      <div className="ai-wizard-step">
        {renderStepNumber('define', 1)}
        <span className={`ai-wizard-step-label ai-wizard-step-label--${getStepStatus('define')}`}>
          Define
        </span>
      </div>
      <div
        className={`ai-wizard-step-connector ${
          getStepStatus('define') === 'completed' ? 'ai-wizard-step-connector--completed' : ''
        }`}
      />
      <div className="ai-wizard-step">
        {renderStepNumber('generate', 2)}
        <span
          className={`ai-wizard-step-label ai-wizard-step-label--${getStepStatus('generate')}`}
        >
          Generate
        </span>
      </div>
      <div
        className={`ai-wizard-step-connector ${
          getStepStatus('generate') === 'completed' ? 'ai-wizard-step-connector--completed' : ''
        }`}
      />
      <div className="ai-wizard-step">
        {renderStepNumber('review', 3)}
        <span className={`ai-wizard-step-label ai-wizard-step-label--${getStepStatus('review')}`}>
          Review
        </span>
      </div>
    </div>
  );

  const renderContent = () => {
    switch (currentStep) {
      case 'define':
        return (
          <DefineStep
            onSubmit={handleDefineSubmit}
            onBack={onBack || handleClose}
            initialValues={formValues}
            serverError={defineError}
          />
        );
      case 'generate':
        return (
          <GenerateStep
            ruleName={formValues?.ruleName || ''}
            onComplete={handleGenerationComplete}
            hasError={generationError}
          />
        );
      case 'review':
        return ruleData ? (
          <ReviewStep
            ruleData={ruleData}
            onBack={handleReviewBack}
            onRegenerate={handleRegenerate}
            onActivate={handleActivateRule}
            loading={loading}
            hasError={generationError}
          />
        ) : null;
    }
  };

  return (
    <div className="ai-wizard-overlay">
      <div className={`ai-wizard-modal${currentStep === 'define' ? ' ai-wizard-modal--define' : ''}${currentStep === 'generate' ? ' ai-wizard-modal--generate' : ''}${currentStep === 'review' ? ' ai-wizard-modal--review' : ''}`}>
        <div className="ai-wizard-topbar">
          <div className="ai-wizard-breadcrumb">
            <span className="ai-wizard-breadcrumb-parent">New Rule</span>
            <img src={ChevronRightSvg} alt="" className="ai-wizard-breadcrumb-separator" />
            <span className="ai-wizard-breadcrumb-current">{getBreadcrumbText()}</span>
          </div>
          {renderStepper()}
          <button className="ai-wizard-close" onClick={handleClose} aria-label="Close wizard">
            <img src={XSvg} alt="" className="ai-wizard-close-icon" />
          </button>
        </div>
        <div className="ai-wizard-content">
          <div className="ai-wizard-content-inner">{renderContent()}</div>
        </div>
      </div>
    </div>
  );
}
