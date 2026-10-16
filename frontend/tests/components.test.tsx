import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { LoginForm, PersonalityTest, StudyMatchProvider, StudyRoomCard } from '../src/studymatch-ui';
import type { StudyRoom } from '../src/studymatch-ui';
import { ComponentPreview } from '../src/ComponentPreview';

afterEach(cleanup);
beforeEach(async () => { await i18n.changeLanguage('en'); });

describe('presentational components', () => {
  it('translates backend field errors and retains entered values when language changes', async () => {
    const submit = vi.fn(async () => {
      throw { code: 'VALIDATION_FAILED', fieldErrors: [
        { field: 'email', code: 'EMAIL' },
        { field: 'password', code: 'SIZE', params: { min: 8, max: 20 } },
      ] };
    });
    render(<LoginForm onSubmit={submit} />);
    fireEvent.change(screen.getByLabelText('Institutional email'), { target: { value: 'person@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
    await screen.findByText('Enter a valid email address.');
    expect(screen.getByText('Use between 8 and 20 characters or items.')).toBeTruthy();
    expect(submit).toHaveBeenCalledWith({ email: 'person@example.com', password: 'short' });
    await act(async () => { await i18n.changeLanguage('pt'); });
    expect(screen.getByText('Introduza um endereço de email válido.')).toBeTruthy();
    expect(screen.getByText('Utilize entre 8 e 20 caracteres ou elementos.')).toBeTruthy();
    expect((screen.getByLabelText('Email institucional') as HTMLInputElement).value).toBe('person@example.com');
  });

  it('displays business error parameters without exposing arbitrary exception text', async () => {
    const submit = vi.fn(async () => { throw {code:'CAPACITY_BELOW_PARTICIPANTS',params:{participants:5}}; });
    const { rerender } = render(<LoginForm onSubmit={submit} />);
    fireEvent.click(screen.getByRole('button', {name:'Log in'}));
    await screen.findByText('Capacity cannot be below 5 participants.');
    rerender(<LoginForm onSubmit={async () => { throw new Error('private backend details'); }} />);
    fireEvent.click(screen.getByRole('button', {name:'Log in'}));
    await screen.findByText('Something went wrong. Please try again.');
    expect(screen.queryByText('private backend details')).toBeNull();
  });

  it('uses supplied StudyRoom flags instead of inferring eligibility from status or dates', () => {
    const room: StudyRoom = { id:'room',subject:'Subject',topic:'Topic',start:'2020-01-01T10:00:00',end:'2020-01-01T11:00:00',
      tutor:{id:'tutor',name:'Tutor'},capacity:1,participants:[],status:'FULL' };
    const onJoin = vi.fn();
    const { rerender } = render(<StudyRoomCard room={room} onJoin={onJoin} canJoin={false} />);
    expect(screen.queryByRole('button', {name:'Join'})).toBeNull();
    rerender(<StudyRoomCard room={room} onJoin={onJoin} canJoin />);
    fireEvent.click(screen.getByRole('button', {name:'Join'}));
    expect(onJoin).toHaveBeenCalledWith(room);
    rerender(<StudyRoomCard room={room} onJoin={onJoin} canJoin joinBlockedCode="STUDYROOM_FULL" />);
    expect((screen.getByRole('button', {name:'Join'}) as HTMLButtonElement).disabled).toBe(true);
  });

  it('submits personality answers without scoring or default questions', async () => {
    const submit = vi.fn(async () => {});
    render(<PersonalityTest questions={[{id:'q1',text:'A supplied question'}]} onSubmit={submit} />);
    fireEvent.click(screen.getByRole('radio', {name:'Strongly agree'}));
    fireEvent.click(screen.getByRole('button', {name:'Submit'}));
    await waitFor(() => expect(submit).toHaveBeenCalledWith({q1:5}));
    expect(screen.getAllByRole('group')).toHaveLength(1);
  });

  it('switches the demo to European Portuguese', async () => {
    render(<StudyMatchProvider><ComponentPreview /></StudyMatchProvider>);
    fireEvent.change(screen.getByLabelText('Language'), {target:{value:'pt'}});
    await screen.findByRole('heading', {name:'Componentes StudyMatch'});
    expect(screen.getByRole('button', {name:'Iniciar sessão'})).toBeTruthy();
  });
});
