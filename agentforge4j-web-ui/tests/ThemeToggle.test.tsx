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
});
