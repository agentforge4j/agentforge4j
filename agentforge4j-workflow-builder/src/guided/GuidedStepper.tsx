// SPDX-License-Identifier: Apache-2.0

export type GuidedStage = {
  label: string;
  complete: boolean;
  actionLabel?: string;
};

type GuidedStepperProps = {
  stages: GuidedStage[];
  activeIndex: number;
  onStageAction?: (index: number) => void;
};

export function GuidedStepper({ stages, activeIndex, onStageAction }: GuidedStepperProps) {
  return (
    <section className="wf-guided-stepper wf-panel">
      <ol className="wf-guided-stepper__list">
        {stages.map((stage, index) => {
          const isActive = index === activeIndex && !stage.complete;
          return (
            <li key={stage.label} className="wf-guided-stepper__item">
              <span
                className={[
                  'wf-guided-stepper__marker',
                  stage.complete ? 'wf-guided-stepper__marker--done' : '',
                  isActive ? 'wf-guided-stepper__marker--active' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                aria-hidden
              >
                {stage.complete ? '✓' : '○'}
              </span>
              <div className="wf-guided-stepper__label">
                <span
                  className={[
                    'wf-guided-stepper__text',
                    stage.complete ? 'wf-guided-stepper__text--done' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                >
                  {index + 1}. {stage.label}
                </span>
              </div>
              {isActive && stage.actionLabel && onStageAction ? (
                <button
                  type="button"
                  className="wf-button wf-button--secondary wf-guided-stepper__action"
                  onClick={() => onStageAction(index)}
                >
                  {stage.actionLabel}
                </button>
              ) : null}
            </li>
          );
        })}
      </ol>
    </section>
  );
}
