// SPDX-License-Identifier: Apache-2.0
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ThemeToggle from '@/components/ThemeToggle';
import { ThemeProvider } from '@/theme/ThemeContext';
import { THEME_STORAGE_KEY } from '@/theme/theme';

function renderToggle() {
  return render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    media: '(prefers-color-scheme: dark)',
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  });
});

afterEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
});

describe('ThemeToggle: accessible name and state', () => {
  test('the trigger button has an accessible name naming the current mode, and is collapsed by default', () => {
    renderToggle();
    const trigger = screen.getByRole('button', { name: /Theme: System/ });
    expect(trigger).toHaveAttribute('aria-haspopup', 'menu');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  test('the accessible name updates once an explicit selection is made', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.click(screen.getByRole('button', { name: /Theme: System/ }));
    await user.click(screen.getByRole('menuitemradio', { name: 'Dark' }));
    expect(screen.getByRole('button', { name: /Theme: Dark/ })).toBeInTheDocument();
  });
});

describe('ThemeToggle: menu structure', () => {
  test('opens a menu with exactly three light/dark/system options, does not depend on colour alone', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.click(screen.getByRole('button', { name: /Theme/ }));
    const menu = screen.getByRole('menu', { name: 'Theme' });
    expect(menu).toBeInTheDocument();
    // Every option carries a real, distinct text label — not just an icon/colour swatch.
    expect(screen.getByRole('menuitemradio', { name: 'Light' })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: 'Dark' })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: 'System' })).toBeInTheDocument();
  });

  test('exactly the current mode is marked aria-checked=true', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.click(screen.getByRole('button', { name: /Theme/ }));
    expect(screen.getByRole('menuitemradio', { name: 'System' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('menuitemradio', { name: 'Light' })).toHaveAttribute('aria-checked', 'false');
    expect(screen.getByRole('menuitemradio', { name: 'Dark' })).toHaveAttribute('aria-checked', 'false');
  });
});

describe('ThemeToggle: keyboard operation', () => {
  test('activating the trigger with the keyboard opens the menu', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.tab();
    expect(screen.getByRole('button', { name: /Theme/ })).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
  });

  test('ArrowDown/ArrowUp move focus between menu items', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.click(screen.getByRole('button', { name: /Theme/ }));
    // Focus starts on the currently-checked item (System, index 2).
    expect(screen.getByRole('menuitemradio', { name: 'System' })).toHaveFocus();
    await user.keyboard('{ArrowUp}');
    expect(screen.getByRole('menuitemradio', { name: 'Dark' })).toHaveFocus();
    await user.keyboard('{ArrowUp}');
    expect(screen.getByRole('menuitemradio', { name: 'Light' })).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitemradio', { name: 'Dark' })).toHaveFocus();
  });

  test('Escape closes the menu and returns focus to the trigger button', async () => {
    const user = userEvent.setup();
    renderToggle();
    const trigger = screen.getByRole('button', { name: /Theme/ });
    await user.click(trigger);
    expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu', { name: 'Theme' })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  // APG menu-button pattern: the arrow keys open the menu from the closed trigger, not only
  // Enter/Space. Initial focus goes to the CHECKED item (the menuitemradio variant of the
  // pattern), same as every other open path in this component.
  test('ArrowDown on the closed trigger opens the menu with focus on the checked item', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.tab();
    expect(screen.getByRole('button', { name: /Theme/ })).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: 'System' })).toHaveFocus();
  });

  test('ArrowUp on the closed trigger also opens the menu', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.tab();
    await user.keyboard('{ArrowUp}');
    expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
  });

  test('selecting an option with the keyboard applies it, persists it, and closes the menu', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.click(screen.getByRole('button', { name: /Theme/ }));
    await user.keyboard('{ArrowUp}'); // System -> Dark
    await user.keyboard('{Enter}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});

describe('ThemeToggle: dismissal', () => {
  test('clicking outside the menu closes it', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <div>
          <ThemeToggle />
          <button>Outside</button>
        </div>
      </ThemeProvider>,
    );
    await user.click(screen.getByRole('button', { name: /Theme/ }));
    expect(screen.getByRole('menu')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Outside' }));
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  test('clicking the trigger while the menu is open closes it (no focusout-then-toggle reopen)', async () => {
    const user = userEvent.setup();
    renderToggle();
    const trigger = screen.getByRole('button', { name: /Theme/ });
    await user.click(trigger);
    expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
    // Without the trigger's mousedown preventDefault, this press first moves focus off the menu
    // item (focusout closes the menu) and the click's toggle then reopens it — the menu would
    // still be present here.
    await user.click(trigger);
    expect(screen.queryByRole('menu', { name: 'Theme' })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  test('a press on the menu popup itself (its border/padding, not an item) does not dismiss it', async () => {
    const user = userEvent.setup();
    renderToggle();
    await user.click(screen.getByRole('button', { name: /Theme/ }));
    const menu = screen.getByRole('menu', { name: 'Theme' });
    // Target the menu element itself — the surface between/around items. This must count as an
    // inside press (and must not steal focus from the focused item, which would close the menu
    // via focusout), not as an outside-dismissal.
    await user.click(menu);
    expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: 'System' })).toHaveFocus();
  });

  test('Escape dismisses only this menu: a document-level Escape listener (the mobile nav panel) is not also torn down by the same press', async () => {
    const user = userEvent.setup();
    const outerEscapeListener = vi.fn();
    document.addEventListener('keydown', outerEscapeListener);
    try {
      renderToggle();
      const trigger = screen.getByRole('button', { name: /Theme/ });
      await user.click(trigger);
      expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
      await user.keyboard('{Escape}');
      expect(screen.queryByRole('menu', { name: 'Theme' })).not.toBeInTheDocument();
      expect(trigger).toHaveFocus();
      // Innermost-popup semantics: the press that closed this menu never reached document level.
      expect(outerEscapeListener).not.toHaveBeenCalled();
      // Negative control: with the menu closed, the next Escape DOES reach the document level —
      // proving the silence above came from stopPropagation, not a broken test setup.
      await user.keyboard('{Escape}');
      expect(outerEscapeListener).toHaveBeenCalledTimes(1);
    } finally {
      document.removeEventListener('keydown', outerEscapeListener);
    }
  });

  // C176-02: roving tabindex means only the checked item is ever Tab-reachable, so a Tab or
  // Shift+Tab press while a menu item is focused always moves focus outside the menu (there is
  // no second in-menu stop) — the menu must close behind it rather than remain open as a stale
  // overlay while the page's normal tab order continues elsewhere.
  test('pressing Tab while a menu item is focused closes the menu and lets focus advance to the next element', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <div>
          <ThemeToggle />
          <button>After</button>
        </div>
      </ThemeProvider>,
    );
    await user.click(screen.getByRole('button', { name: /Theme/ }));
    expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
    await user.tab();
    expect(screen.queryByRole('menu', { name: 'Theme' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'After' })).toHaveFocus();
  });

  test('pressing Shift+Tab while a menu item is focused closes the menu (focus moves back to the trigger)', async () => {
    const user = userEvent.setup();
    renderToggle();
    const trigger = screen.getByRole('button', { name: /Theme/ });
    await user.click(trigger);
    expect(screen.getByRole('menu', { name: 'Theme' })).toBeInTheDocument();
    await user.tab({ shift: true });
    expect(screen.queryByRole('menu', { name: 'Theme' })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
});
