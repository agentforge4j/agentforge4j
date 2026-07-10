import { describe, expect, it, vi } from 'vitest';
import { importWorkflowFromFilePicker } from '../src/io/browser/upload';

describe('importWorkflowFromFilePicker', () => {
  it('restricts the built-in file picker to zip, not the unvalidated plain-json draft format', () => {
    let captured: HTMLInputElement | undefined;
    const originalCreateElement = document.createElement.bind(document);
    const createSpy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === 'input') {
        captured = el as HTMLInputElement;
        vi.spyOn(el, 'click').mockImplementation(() => {});
      }
      return el;
    });

    void importWorkflowFromFilePicker().catch(() => {});

    expect(captured?.accept).toBe('application/zip,.zip');
    createSpy.mockRestore();
  });
});
