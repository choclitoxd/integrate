import { Icon } from '@/shared/ui';

/** Insignia de conexión del módulo del Gestor (FE-DIR-001, Directiva 6): texto e icono, no solo color. */
export function ConnectionBadge({ online }: { online: boolean }) {
  return (
    <p
      role="status"
      className={`inline-flex min-h-9 shrink-0 items-center gap-1.5 rounded-full border-2 px-3 text-sm font-bold ${
        online
          ? 'border-success-border bg-success-bg text-success-text'
          : 'border-warning-border bg-warning-bg text-warning-text'
      }`}
    >
      <Icon name={online ? 'wifi' : 'wifiOff'} size={18} />
      {online ? 'En línea' : 'Sin conexión'}
    </p>
  );
}
