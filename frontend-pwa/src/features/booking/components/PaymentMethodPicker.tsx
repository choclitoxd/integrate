import type { PaymentMethod } from '../api';

const METHODS: readonly { value: PaymentMethod; label: string; hint: string }[] = [
  { value: 'PSE', label: 'PSE', hint: 'Débito desde tu cuenta bancaria' },
  { value: 'CARD', label: 'Tarjeta', hint: 'Crédito o débito' },
  { value: 'NEQUI', label: 'Nequi', hint: 'Billetera digital' },
  { value: 'DAVIPLATA', label: 'Daviplata', hint: 'Billetera digital' },
];

interface PaymentMethodPickerProps {
  readonly value: PaymentMethod;
  readonly onChange: (value: PaymentMethod) => void;
  readonly disabled?: boolean;
}

/** Método de pago en COP (T-1.10d): radio nativo, tarjetas de ≥ 44 px. */
export function PaymentMethodPicker({
  value,
  onChange,
  disabled = false,
}: PaymentMethodPickerProps) {
  return (
    <fieldset className="mt-6" disabled={disabled}>
      <legend className="text-lg font-bold text-ink">Método de pago</legend>
      <div className="mt-3 grid grid-cols-2 gap-2">
        {METHODS.map((method) => (
          <label
            key={method.value}
            className="grid min-h-16 cursor-pointer grid-cols-[auto_1fr] items-start gap-x-2 rounded-xl border-2 border-border bg-canvas px-3 py-3 has-checked:border-brand has-checked:bg-brand-soft has-disabled:cursor-not-allowed"
          >
            <input
              type="radio"
              name="metodo"
              value={method.value}
              checked={value === method.value}
              onChange={() => {
                onChange(method.value);
              }}
              className="row-span-2 mt-1 size-5 shrink-0 accent-brand"
            />
            <span className="font-semibold text-ink">{method.label}</span>
            <span className="text-sm text-ink-muted">{method.hint}</span>
          </label>
        ))}
      </div>
      <p className="mt-2 text-sm text-ink-muted">
        El pago se completa en la pasarela segura; Intégrate no ve los datos de tu tarjeta ni de tu
        banco.
      </p>
    </fieldset>
  );
}
