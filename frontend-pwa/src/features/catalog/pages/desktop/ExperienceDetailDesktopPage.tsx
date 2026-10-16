import { Link } from 'react-router';

import { formatCop } from '@/shared/lib';
import { buttonClassName, Icon } from '@/shared/ui';

import { DetailHeader, DetailSections } from '../../components/ExperienceDetailParts';
import { ExperienceFacts } from '../../components/ExperienceFacts';
import { ExperienceDetailLoader } from '../ExperienceDetailPage';

/** Detalle en escritorio (ADR-0020 §7): ficha a la izquierda y tarjeta de reserva fija a la derecha. */
export function ExperienceDetailDesktopPage() {
  return (
    <ExperienceDetailLoader>
      {(detail) => (
        <div className="grid grid-cols-[minmax(0,1fr)_21rem] items-start gap-12">
          <div>
            <DetailHeader detail={detail} />
            <DetailSections detail={detail} />
          </div>

          <aside
            aria-labelledby="reserva-titulo"
            className="sticky top-24 rounded-xl border-2 border-border-subtle bg-surface-elevated p-6"
          >
            <h2 id="reserva-titulo" className="sr-only">
              Reserva
            </h2>
            <p>
              <span className="block text-3xl font-bold text-ink tabular-nums">
                {formatCop(detail.summary.unitPriceCop)}
              </span>
              <span className="text-ink-muted">por persona</span>
            </p>
            <div className="mt-5 border-t border-border-subtle pt-5">
              <ExperienceFacts experience={detail.summary} variant="full" />
            </div>
            <Link
              to={`/experiencias/${detail.summary.id}/franjas`}
              className={buttonClassName('primary', 'mt-6 w-full')}
            >
              <Icon name="calendar" />
              Ver fechas disponibles
            </Link>
          </aside>
        </div>
      )}
    </ExperienceDetailLoader>
  );
}
