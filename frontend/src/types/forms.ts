import type { FieldError } from '../api/errors';
import type { Role } from './enums';
export interface FormFeedbackProps { fieldErrors?: FieldError[] }
export interface LoginInput { email: string; password: string }
export interface ChangePasswordInput { current?: string; password: string; confirmPassword: string }
export interface CreateAccountInput { role: Extract<Role, 'STAFF' | 'TEACHER'>; name: string; personalEmail: string }
export interface RegisterStudentInput { name: string; studentNumber: string; personalEmail: string; programme: string; curricularYear: number }
