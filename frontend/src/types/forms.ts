import type { FieldError } from '../api/errors';
import type { EmployeeRole } from './accounts';
export interface FormFeedbackProps { fieldErrors?: FieldError[] }
export interface LoginInput { email: string; password: string }
export interface ResetPasswordInput { code: string; newPassword: string }
export interface ResetPasswordRequest { email: string; code: string; newPassword: string }
export interface ChangePasswordInput { currentPassword?: string; newPassword: string }
export interface CreateAccountInput { type: EmployeeRole; name: string; personalEmail: string; username: string }
