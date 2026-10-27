/** Estilos del marco y del <input> de los campos de texto, compartidos por TextField y PhoneField. */

export const INPUT_CLASS =
  'min-h-12 w-full min-w-0 bg-transparent px-3 text-base text-ink placeholder:text-ink-subtle focus:outline-none focus-visible:outline-none focus-visible:shadow-none';

export const FIELD_WRAPPER_CLASS =
  'relative mt-1.5 flex items-center rounded-lg border-2 bg-canvas transition duration-200 ease-in-out before:pointer-events-none before:absolute before:inset-0 before:rounded-lg before:opacity-0 before:shadow-[0_0.5rem_1.5rem_-0.75rem_color-mix(in_oklab,var(--color-brand)_55%,transparent)] before:transition-opacity before:duration-300 before:ease-in-out focus-within:-translate-y-1 focus-within:before:opacity-100 motion-reduce:focus-within:translate-y-0';

export function fieldBorderClass(error: boolean): string {
  return error ? 'border-danger-border' : 'border-border focus-within:border-brand';
}
