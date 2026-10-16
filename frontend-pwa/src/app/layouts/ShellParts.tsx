import { useLocation, useNavigate } from 'react-router';

import { Icon } from '@/shared/ui';

export function BackButton({ fallback, className }: { fallback: string; className: string }) {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <button
      type="button"
      onClick={() => {
        // Con historial dentro de la app se vuelve atrás (conserva filtros y scroll).
        if (location.key !== 'default') void navigate(-1);
        else void navigate(fallback);
      }}
      className={`inline-flex min-h-11 items-center gap-1 rounded-lg pr-3 pl-1 font-semibold ${className}`}
    >
      <Icon name="chevronLeft" size={24} />
      Atrás
    </button>
  );
}
