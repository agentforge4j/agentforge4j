// SPDX-License-Identifier: Apache-2.0

import type { RefObject } from 'react';
import { useLayoutEffect, useRef, useState } from 'react';

/**
 * Width (px) below which the builder's own rendered container is too narrow to host
 * the editor. Matches the narrow-viewport breakpoint already used throughout
 * `workflow-builder.css` (`47.9375rem`, i.e. 767px at the standard 16px root font size),
 * kept as a single source of truth here for the container-based gate.
 */
export const NARROW_CONTAINER_BREAKPOINT_PX = 767;

export interface NarrowContainerGate<T extends HTMLElement> {
  /** Attach to the element whose rendered width should be measured. */
  containerRef: RefObject<T | null>;
  /** True once the measured container width is below {@link NARROW_CONTAINER_BREAKPOINT_PX}. */
  isNarrow: boolean;
}

/**
 * Measures the width of the builder's own rendered root element via `ResizeObserver` —
 * never `window.innerWidth` — since the builder may be embedded in a host panel of
 * arbitrary size unrelated to the browser viewport. The observed element does not
 * unmount across narrow/wide transitions (the caller swaps its children instead), so a
 * single observer subscription tracks it for the component's whole lifetime.
 *
 * A measured width of `0` (not yet laid out — e.g. before first paint, or in test
 * environments without real layout) is treated as "not yet measured" rather than
 * "narrow", so the gate never fires before the container has a real size.
 *
 * The first measurement is taken SYNCHRONOUSLY (layout effect + `getBoundingClientRect`)
 * before the browser paints: without it, a phone would mount — and visibly flash — the
 * full editor (React Flow initialization included) for a frame while waiting for the
 * observer's first asynchronous callback, and the gated interactions would be briefly
 * reachable. The observer then keeps the measurement current across resizes.
 */
export function useNarrowContainerGate<T extends HTMLElement>(): NarrowContainerGate<T> {
  const containerRef = useRef<T | null>(null);
  const [isNarrow, setIsNarrow] = useState(false);

  useLayoutEffect(() => {
    const node = containerRef.current;
    if (!node) {
      return undefined;
    }
    const initialWidth = node.getBoundingClientRect().width;
    if (initialWidth > 0) {
      setIsNarrow(initialWidth < NARROW_CONTAINER_BREAKPOINT_PX);
    }
    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (!entry) {
        return;
      }
      const width = entry.contentRect.width;
      if (width > 0) {
        setIsNarrow(width < NARROW_CONTAINER_BREAKPOINT_PX);
      }
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return { containerRef, isNarrow };
}
