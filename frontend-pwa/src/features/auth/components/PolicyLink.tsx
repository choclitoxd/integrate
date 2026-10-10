import { Icon } from '@/shared/ui';

import type { PrivacyPolicy } from '../api';

/** Enlace al documento que el titular acepta: título y versión vigentes (ADR-0013 §1). */
export function PolicyLink({ policy }: { policy: PrivacyPolicy }) {
  return (
    <a
      href={policy.documentUrl}
      target="_blank"
      rel="noreferrer"
      className="inline-flex min-h-11 items-center gap-1.5 font-semibold text-brand underline underline-offset-4"
    >
      Leer la {policy.title} ({policy.version})
      <Icon name="external" size={18} />
      <span className="sr-only">(se abre en otra pestaña)</span>
    </a>
  );
}
