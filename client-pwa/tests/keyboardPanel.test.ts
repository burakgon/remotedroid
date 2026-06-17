import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/svelte';
import KeyboardPanel from '../src/lib/ui/KeyboardPanel.svelte';

describe('KeyboardPanel', () => {
  it('sends the whole text at once when Gönder is pressed', async () => {
    const send = vi.fn();
    const { getByRole, getByText } = render(KeyboardPanel, { props: { send } });
    await fireEvent.input(getByRole('textbox'), { target: { value: 'interstellar' } });
    await fireEvent.click(getByText('Gönder'));
    expect(send).toHaveBeenCalledWith({ type: 'text', value: 'interstellar' });
  });

  it('sends submit when Enter/Ara is pressed', async () => {
    const send = vi.fn();
    const { getByText } = render(KeyboardPanel, { props: { send } });
    await fireEvent.click(getByText('Enter / Ara'));
    expect(send).toHaveBeenCalledWith({ type: 'submit' });
  });
});
