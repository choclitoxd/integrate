export { ConsentDialog } from './components/ConsentDialog';
export { ConsentGate } from './components/ConsentGate';
export { PolicyLink } from './components/PolicyLink';
export { AccountPage } from './pages/AccountPage';
export { ActivatePage } from './pages/ActivatePage';
export { LoginPage } from './pages/LoginPage';
export { RegisterPage } from './pages/RegisterPage';
export { authKeys, useCurrentPolicy, useMyConsents } from './queries';
export { returnQuery, safeReturnPath } from './returnPath';
// Reutilizados por la reserva (ADR-0015 §3): mismas reglas de documento y celular que el registro.
export type { DocType, PrivacyPolicy } from './api';
export {
  DOC_TYPES,
  fieldErrorsOf,
  normalizePhone,
  NUMERIC_DOC_TYPES,
  type FieldErrors,
} from './forms';
