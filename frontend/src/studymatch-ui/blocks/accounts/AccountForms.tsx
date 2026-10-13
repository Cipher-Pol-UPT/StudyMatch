import { AtSign, BadgeCheck, Hash, Lock, LockOpen, UserRound } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { useOptionalAuth } from '../../auth/AuthProvider';
import type { CreateAccountInput, RegisterStudentInput } from '../../auth/types';
import { Button } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { Select, type SelectOption } from '../../components/field/Select';
import { TextField } from '../../components/field/TextField';
import { Banner } from '../../components/overlay/Banner';
import { ConfirmDialog } from '../../components/overlay/Dialog';
import { Segmented } from '../../components/segmented/Segmented';
import { isEmail } from '../auth/LoginForm';
import '../auth/auth.css';

function Success({ title, children, onAgain, againLabel }: { title: string; children: ReactNode; onAgain: () => void; againLabel: string }) {
  return (
    <div className="sm-auth__status" role="status">
      <div className="sm-auth__status-art" style={{ background: 'var(--sm-success-50)', color: 'var(--sm-success)' }}>
        <BadgeCheck size={40} strokeWidth={1.75} aria-hidden />
      </div>
      <h3 className="sm-auth__title" style={{ fontSize: 30 }}>
        {title}
      </h3>
      <div className="sm-auth__subtitle">{children}</div>
      <div className="sm-auth__actions">
        <Button variant="secondary" size="lg" block onClick={onAgain}>
          {againLabel}
        </Button>
      </div>
    </div>
  );
}

// ─── Create Account (Admin: Staff and Teacher only) ────────
export interface CreateAccountFormProps {
  onSubmit?: (input: CreateAccountInput) => Promise<{ id: string }>;
  onCreated?: (input: CreateAccountInput & { id: string }) => void;
  /** Required institutional domain, e.g. "uni.example". */
  domain?: string;
  /** Render without the white card. */
  bare?: boolean;
}

/** Admins create Staff and Teacher accounts. A one-time password goes to the institutional email. */
export function CreateAccountForm({ onSubmit, onCreated, domain = 'uni.example', bare }: CreateAccountFormProps) {
  const auth = useOptionalAuth();
  const [role, setRole] = useState<'teacher' | 'staff'>('teacher');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [errors, setErrors] = useState<{ name?: string; email?: string; form?: string }>({});
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState<CreateAccountInput | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: typeof errors = {};
    if (name.trim().split(/\s+/).length < 2) errs.name = 'Enter the full name (first and last name).';
    if (!isEmail(email)) errs.email = 'Enter a valid email.';
    else if (domain && !email.trim().toLowerCase().endsWith(`@${domain}`)) errs.email = `Use an institutional address ending in @${domain}.`;
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const run = onSubmit ?? auth?.adapter.createAccount?.bind(auth.adapter);
    if (!run) throw new Error('CreateAccountForm needs an adapter with createAccount() or an onSubmit prop.');
    const input: CreateAccountInput = { role, name: name.trim(), institutionalEmail: email.trim().toLowerCase() };
    setBusy(true);
    try {
      const { id } = await run(input);
      setCreated(input);
      onCreated?.({ ...input, id });
    } catch (err) {
      setErrors({ form: err instanceof Error ? err.message : 'Couldn’t create the account.' });
    } finally {
      setBusy(false);
    }
  };

  const body = created ? (
    <Success
      title="Account created"
      againLabel="Create another account"
      onAgain={() => {
        setCreated(null);
        setName('');
        setEmail('');
      }}
    >
      <b style={{ color: 'var(--sm-black)', fontWeight: 500 }}>{created.name}</b> ({created.role === 'teacher' ? 'Teacher' : 'Staff'}) will receive a one-time password at <b style={{ color: 'var(--sm-black)', fontWeight: 500 }}>{created.institutionalEmail}</b>. It works for one login and expires in 7 days.
    </Success>
  ) : (
    <form className="sm-auth__form" style={{ marginTop: 0 }} onSubmit={submit} noValidate>
      <div>
        <div className="sm-eyebrow" style={{ marginBottom: 10 }}>
          Account type
        </div>
        <Segmented
          label="Account type"
          block
          value={role}
          onValueChange={setRole}
          options={[
            { value: 'teacher', label: 'Teacher' },
            { value: 'staff', label: 'Staff' },
          ]}
        />
        <p className="sm-auth__muted" style={{ margin: '10px 4px 0' }}>
          {role === 'teacher' ? 'Teaches Subject Editions: creates assignments, grades and forms groups.' : 'Academic services: registers students, subjects, enrolments and attendance.'} Students are registered by Staff.
        </p>
      </div>
      {errors.form && <Banner tone="danger">{errors.form}</Banner>}
      <TextField label="Full name" icon={UserRound} autoComplete="off" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} />
      <TextField label="Institutional email" type="email" icon={AtSign} autoComplete="off" placeholder={`name@${domain}`} value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email} />
      <Banner tone="info" title="One-time password">
        We’ll email a one-time password to this address. On first login they’ll choose their own password.
      </Banner>
      <Button type="submit" size="lg" block loading={busy}>
        Create {role === 'teacher' ? 'teacher' : 'staff'} account
      </Button>
    </form>
  );

  return bare ? body : <Card padding="lg">{body}</Card>;
}

// ─── Register Student (Staff) ──────────────────────────────
export interface RegisterStudentFormProps {
  programmes: SelectOption[];
  onSubmit?: (input: RegisterStudentInput) => Promise<{ id: string; institutionalEmail: string }>;
  onRegistered?: (input: RegisterStudentInput & { id: string; institutionalEmail: string }) => void;
  /** How institutional emails are generated from the student number. */
  institutionalEmailFor?: (studentNumber: string) => string;
  /** Number of curricular years in the degree. */
  years?: number;
  bare?: boolean;
}

/** Staff register a student. The institutional email is generated; a one-time password goes to the personal email. */
export function RegisterStudentForm({ programmes, onSubmit, onRegistered, institutionalEmailFor = (n) => `${n}@students.uni.example`, years = 3, bare }: RegisterStudentFormProps) {
  const auth = useOptionalAuth();
  const [form, setForm] = useState({ name: '', number: '', personalEmail: '', programme: '', year: 1 });
  const [errors, setErrors] = useState<Partial<Record<'name' | 'number' | 'personalEmail' | 'programme' | 'form', string>>>({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<{ name: string; institutionalEmail: string; personalEmail: string } | null>(null);
  const set = (k: keyof typeof form, v: string | number) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: typeof errors = {};
    if (form.name.trim().split(/\s+/).length < 2) errs.name = 'Enter the full name.';
    if (!/^\d{5,10}$/.test(form.number)) errs.number = 'Student numbers have 5 to 10 digits.';
    if (!isEmail(form.personalEmail)) errs.personalEmail = 'Enter a valid personal email.';
    if (!form.programme) errs.programme = 'Choose the degree programme.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const run = onSubmit ?? auth?.adapter.registerStudent?.bind(auth.adapter);
    if (!run) throw new Error('RegisterStudentForm needs an adapter with registerStudent() or an onSubmit prop.');
    const input: RegisterStudentInput = { name: form.name.trim(), studentNumber: form.number, personalEmail: form.personalEmail.trim(), programme: form.programme, curricularYear: form.year };
    setBusy(true);
    try {
      const r = await run(input);
      setDone({ name: input.name, institutionalEmail: r.institutionalEmail, personalEmail: input.personalEmail });
      onRegistered?.({ ...input, ...r });
    } catch (err) {
      setErrors({ form: err instanceof Error ? err.message : 'Couldn’t register the student.' });
    } finally {
      setBusy(false);
    }
  };

  const body = done ? (
    <Success
      title="Student registered"
      againLabel="Register another student"
      onAgain={() => {
        setDone(null);
        setForm({ name: '', number: '', personalEmail: '', programme: form.programme, year: form.year });
      }}
    >
      <b style={{ color: 'var(--sm-black)', fontWeight: 500 }}>{done.name}</b> can log in with <b style={{ color: 'var(--sm-black)', fontWeight: 500 }}>{done.institutionalEmail}</b>. The one-time password was sent to {done.personalEmail} and expires in 7 days.
    </Success>
  ) : (
    <form className="sm-auth__form" style={{ marginTop: 0 }} onSubmit={submit} noValidate>
      {errors.form && <Banner tone="danger">{errors.form}</Banner>}
      <TextField label="Full name" icon={UserRound} autoComplete="off" value={form.name} onChange={(e) => set('name', e.target.value)} error={errors.name} />
      <TextField
        label="Student number"
        icon={Hash}
        inputMode="numeric"
        autoComplete="off"
        value={form.number}
        onChange={(e) => set('number', e.target.value.replace(/\D/g, ''))}
        error={errors.number}
        hint={form.number ? <>Institutional email: <b style={{ color: 'var(--sm-black)', fontWeight: 500 }}>{institutionalEmailFor(form.number)}</b></> : 'The institutional email is generated from it.'}
      />
      <TextField label="Personal email" type="email" icon={AtSign} autoComplete="off" value={form.personalEmail} onChange={(e) => set('personalEmail', e.target.value)} error={errors.personalEmail} hint="The one-time password is sent here." />
      <Select label="Degree programme" placeholder="Choose a programme" options={programmes} value={form.programme} onChange={(e) => set('programme', e.target.value)} error={errors.programme} />
      <div>
        <div className="sm-eyebrow" style={{ margin: '4px 0 10px' }}>
          Curricular year
        </div>
        <Segmented
          label="Curricular year"
          block
          value={form.year}
          onValueChange={(v) => set('year', v)}
          options={Array.from({ length: years }, (_, i) => ({ value: i + 1, label: `Year ${i + 1}` }))}
        />
      </div>
      <Button type="submit" size="lg" block loading={busy}>
        Register student
      </Button>
    </form>
  );

  return bare ? body : <Card padding="lg">{body}</Card>;
}

// ─── Block / unblock ───────────────────────────────────────
export interface BlockAccountDialogProps {
  open: boolean;
  onClose: () => void;
  user: { id: string; name: string; blocked?: boolean };
  /** Defaults to the adapter's setBlocked(). */
  onConfirm?: (reason: string) => Promise<void> | void;
  onDone?: (blocked: boolean) => void;
}

/** Block requires a reason; accounts are never deleted (GR5). Unblock restores access. */
export function BlockAccountDialog({ open, onClose, user, onConfirm, onDone }: BlockAccountDialogProps) {
  const auth = useOptionalAuth();
  const blocking = !user.blocked;
  const confirm = async (reason: string) => {
    if (onConfirm) await onConfirm(reason);
    else if (auth?.adapter.setBlocked) await auth.adapter.setBlocked(user.id, blocking, reason);
    onDone?.(blocking);
  };
  return blocking ? (
    <ConfirmDialog
      open={open}
      onClose={onClose}
      onConfirm={confirm}
      tone="danger"
      icon={Lock}
      title={`Block ${user.name}?`}
      description="They won’t be able to log in or join new groups. Their groups and academic history are kept."
      confirmLabel="Block account"
      reason={{ label: 'Reason', placeholder: 'e.g. Enrolment suspended by academic services', required: true }}
    />
  ) : (
    <ConfirmDialog open={open} onClose={onClose} onConfirm={confirm} icon={LockOpen} title={`Unblock ${user.name}?`} description="They’ll be able to log in again and be included in new groups." confirmLabel="Unblock account" />
  );
}
