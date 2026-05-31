// SPDX-License-Identifier: Apache-2.0

export function EdgeDefs() {
  return (
    <svg className="wf-edge-defs" aria-hidden="true">
      <defs>
        <linearGradient id="afb-edge-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="var(--afb-blue-400)" />
          <stop offset="100%" stopColor="var(--afb-blue-500)" />
        </linearGradient>
      </defs>
    </svg>
  );
}
