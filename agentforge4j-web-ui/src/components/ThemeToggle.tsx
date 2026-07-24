// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, useState } from 'react';
import { Monitor, Moon, Sun, type LucideIcon } from 'lucide-react';
import { useTheme } from '@/theme/ThemeContext';
import type { ThemeMode } from '@/theme/theme';

interface ThemeOption {
  readonly mode: ThemeMode;
  readonly label: string;
  readonly Icon: LucideIcon;
}

const THEME_OPTIONS: readonly ThemeOption[] = [
  { mode: 'light', label: 'Light', Icon: Sun },
  { mode: 'dark', label: 'Dark', Icon: Moon },
  { mode: 'system', label: 'System', Icon: Monitor },
];

/**
 * A three-way light/dark/system control, not a binary switch pretending to represent a third
 * state. Deliberately a hand-rolled button + `role="menu"` (matching this header's existing
 * hand-rolled mobile-nav disclosure — no listbox/select-styling dependency exists in this module
 * to reach for instead) rather than a native `<select>`: a `role="menuitemradio"` group lets the
 * closed-state button show the CURRENT choice's icon + text together (a native select's closed
 * state can only show one of the three option strings, not an icon), while every option still
 * carries its own visible text label — the icon is decoration, never the only signal of what a
 * given item means or which one is currently selected (`aria-checked` carries that).
 */
export default function ThemeToggle() {
  const { mode, setMode } = useTheme();
  const [open, setOpen] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const itemRefs = useRef<Array<HTMLButtonElement | null>>([]);

  const current = THEME_OPTIONS.find((option) => option.mode === mode) ?? THEME_OPTIONS[2];
  const CurrentIcon = current.Icon;

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
        return;
      }
    };
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (buttonRef.current?.contains(target)) {
        return;
      }
      if (!itemRefs.current.some((el) => el?.contains(target))) {
        setOpen(false);
      }
    };
    // Roving tabindex means only one item is ever Tab-reachable at a time, so any Tab/Shift+Tab
    // press while an item is focused always moves focus OUTSIDE the menu (there is no second
    // in-menu stop for the browser's native tab order to land on) — normal keyboard navigation
    // must close the menu behind it rather than leaving a stale open popup, without trapping
    // focus or intercepting the Tab key itself. `focusout` (unlike `blur`) bubbles, and its
    // `relatedTarget` is the element about to receive focus, so listening on the menu element
    // itself catches every departure — Tab, Shift+Tab (back to the trigger button, a sibling
    // outside this element), and any programmatic focus change — while a focus transfer that
    // stays inside the menu (none exist today, but would if this ever grew a second focusable
    // affordance) is correctly left alone.
    const onFocusOut = (event: FocusEvent) => {
      const next = event.relatedTarget as Node | null;
      if (next && menuRef.current?.contains(next)) {
        return;
      }
      setOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('mousedown', onPointerDown);
    const menuEl = menuRef.current;
    menuEl?.addEventListener('focusout', onFocusOut);
    // Focus the currently-selected item when the menu opens, matching the roving-focus
    // expectation of a `menuitemradio` group (focus starts on the checked item, not always the
    // first).
    const currentIndex = THEME_OPTIONS.findIndex((option) => option.mode === mode);
    itemRefs.current[currentIndex >= 0 ? currentIndex : 0]?.focus();
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('mousedown', onPointerDown);
      menuEl?.removeEventListener('focusout', onFocusOut);
    };
  }, [open, mode]);

  const selectAndClose = (next: ThemeMode) => {
    setMode(next);
    setOpen(false);
    buttonRef.current?.focus();
  };

  const onItemKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowRight') {
      event.preventDefault();
      itemRefs.current[(index + 1) % THEME_OPTIONS.length]?.focus();
    } else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') {
      event.preventDefault();
      itemRefs.current[(index - 1 + THEME_OPTIONS.length) % THEME_OPTIONS.length]?.focus();
    } else if (event.key === 'Home') {
      event.preventDefault();
      itemRefs.current[0]?.focus();
    } else if (event.key === 'End') {
      event.preventDefault();
      itemRefs.current[THEME_OPTIONS.length - 1]?.focus();
    }
  };

  return (
    <div className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Theme: ${current.label}. Activate to change.`}
        onClick={() => setOpen((value) => !value)}
        className="flex items-center justify-center rounded-md p-2 text-fg hover:bg-bg-elevated"
      >
        <CurrentIcon size={20} aria-hidden="true" />
      </button>
      {open && (
        <div
          ref={menuRef}
          role="menu"
          aria-label="Theme"
          className="absolute right-0 z-20 mt-2 w-36 rounded-md border border-border bg-bg-elevated py-1 shadow-md"
        >
          {THEME_OPTIONS.map((option, index) => {
            const checked = option.mode === mode;
            const Icon = option.Icon;
            return (
              <button
                key={option.mode}
                ref={(el) => {
                  itemRefs.current[index] = el;
                }}
                type="button"
                role="menuitemradio"
                aria-checked={checked}
                tabIndex={checked ? 0 : -1}
                onClick={() => selectAndClose(option.mode)}
                onKeyDown={(event) => onItemKeyDown(event, index)}
                className={[
                  'flex w-full items-center gap-2 px-3 py-2 text-left text-sm',
                  checked ? 'font-semibold text-brand' : 'text-fg hover:bg-bg',
                ].join(' ')}
              >
                <Icon size={16} aria-hidden="true" />
                {option.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
