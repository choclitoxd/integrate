import { useId, useState, type SubmitEvent } from 'react';

import { BottomSheet, Button } from '@/shared/ui';

import { omitFilters, type CatalogFilters } from '../filters';
import { FilterFields } from './FilterFields';

interface FilterSheetProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly filters: CatalogFilters;
  readonly onApply: (filters: CatalogFilters) => void;
}

/**
 * Filtros DIVIPOLA, categoría e intensidad en una hoja inferior (T-1.10a). El borrador se aplica
 * solo con "Ver resultados" y se reinicia desde la URL en cada apertura. El <dialog> no se remonta:
 * así el navegador devuelve el foco al botón "Filtros" al cerrar.
 */
export function FilterSheet({ open, onClose, filters, onApply }: FilterSheetProps) {
  const [draft, setDraft] = useState<CatalogFilters>(filters);
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    // Ajuste de estado al cambiar una prop, durante el render (sin efecto ni render intermedio).
    setWasOpen(open);
    if (open) setDraft(filters);
  }
  const formId = useId();

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply(draft);
  }

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="Filtrar experiencias"
      footer={
        <>
          <Button
            variant="secondary"
            className="flex-1"
            onClick={() => {
              setDraft(omitFilters(draft, 'departmentCode', 'category', 'intensity'));
            }}
          >
            Limpiar
          </Button>
          <Button type="submit" form={formId} className="flex-[2]">
            Ver resultados
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} className="grid gap-5">
        <FilterFields draft={draft} onChange={setDraft} />
      </form>
    </BottomSheet>
  );
}
