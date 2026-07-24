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
        // Listener lives on the menu element, not document, so it only ever fires while focus
        // is inside the open menu (guaranteed: opening focuses an item, and any focus departure
        // closes the menu via focusout below). stopPropagation gives Escape innermost-popup
        // semantics: with the mobile nav panel open BEHIND this menu, one press dismisses only
        // this menu and a second press reaches the header's document-level listener for the nav
        // — instead of a single press tearing down both at once.
        event.stopPropagation();
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (buttonRef.current?.contains(target)) {
        return;
      }
      // The whole popup surface counts as "inside" — including its own border/padding, which no
      // item covers. Only a genuinely outside press dismisses (the padding press itself is kept
      // from stealing focus by the menu's onMouseDown preventDefault, so focusout stays quiet).
      if (!menuRef.current?.contains(target)) {
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
    document.addEventListener('mousedown', onPointerDown);
    const menuEl = menuRef.current;
    menuEl?.addEventListener('keydown', onKeyDown);
    menuEl?.addEventListener('focusout', onFocusOut);
    // Focus the currently-selected item when the menu opens, matching the roving-focus
    // expectation of a `menuitemradio` group (focus starts on the checked item, not always the
    // first).
    const currentIndex = THEME_OPTIONS.findIndex((option) => option.mode === mode);
    itemRefs.current[currentIndex >= 0 ? currentIndex : 0]?.focus();
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      menuEl?.removeEventListener('keydown', onKeyDown);
      menuEl?.removeEventListener('focusout', onFocusOut);
    };
  }, [open, mode]);

  // APG menu-button pattern: ArrowDown/ArrowUp on the closed trigger open the menu (activation
  // via Enter/Space already comes free with a native <button>). Focus then lands on the CHECKED
  // item via the open-effect above — the menuitemradio variant of the pattern, where initial
  // focus may go to the checked item rather than strictly first/last.
  const onTriggerKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
    }
  };

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
        // preventDefault keeps a press on the trigger from stealing focus off the focused menu
        // item BEFORE the click event lands — without it, that focus departure fires focusout
        // (menu closes mid-press) and the click's toggle then REOPENS it, so a trigger click
        // could never close the open menu. onClick refocuses the trigger explicitly, restoring
        // the focus a mouse press would otherwise have set on both the open and close paths.
        // ORDER MATTERS in onClick: the toggle must be queued before focus() — focus() fires the
        // menu's focusout synchronously, and its own close-update must land AFTER the toggle in
        // the same batch (toggle→closed, focusout→closed) rather than before it (focusout→closed,
        // toggle→REOPENED).
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => {
          setOpen((value) => !value);
          buttonRef.current?.focus();
        }}
        onKeyDown={onTriggerKeyDown}
        className="flex items-center justify-center rounded-md p-2 text-fg hover:bg-bg-elevated"
      >
        <CurrentIcon size={20} aria-hidden="true" />
      </button>
      {open && (
        <div
          ref={menuRef}
          role="menu"
          aria-label="Theme"
          // A press on the popup's own border/padding (not on an item) must not steal focus from
          // the focused item — focus leaving would fire focusout and dismiss the menu, turning a
          // slightly-off click INSIDE the open popup into an accidental close.
          onMouseDown={(event) => event.preventDefault()}
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
