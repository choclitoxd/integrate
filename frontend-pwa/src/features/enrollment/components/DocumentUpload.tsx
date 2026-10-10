import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react';

import { ApiError, NetworkError, useHttpClient } from '@/shared/api';
import { Icon } from '@/shared/ui';

import { putToStorage, requestUpload, UploadRejectedError, type DocumentKind } from '../api';
import { EMPTY_DOCUMENT, formatFileSize, pdfProblem, type DocumentState } from '../forms';

interface DocumentUploadProps {
  readonly kind: DocumentKind;
  readonly title: string;
  readonly hint: string;
  /** Nombre del input: el formulario lo enfoca si este campo tiene error. */
  readonly name: string;
  readonly value: DocumentState;
  readonly onChange: (next: DocumentState) => void;
  readonly error?: string | undefined;
  readonly required?: boolean;
}

function uploadFailure(error: unknown): string {
  if (error instanceof NetworkError) {
    return 'No se pudo subir: revisa la señal o los datos móviles e inténtalo de nuevo.';
  }
  if (error instanceof ApiError && error.code === 'ENROLLMENT_MANAGER_NOT_ACTIVE') {
    return 'Tu acreditación como gestor no está activa: no puedes subir documentos.';
  }
  if (error instanceof UploadRejectedError) {
    return 'La subida no se completó a tiempo. Elige el archivo otra vez.';
  }
  return 'No se pudo subir el archivo. Inténtalo de nuevo.';
}

/**
 * PDF del enrolamiento con subida directa a S3 (ADR-0018 §3): se valida en el teléfono, se pide la
 * URL prefirmada y se hace el PUT. Solo la llave queda en el formulario; el archivo no se guarda.
 */
export function DocumentUpload({
  kind,
  title,
  hint,
  name,
  value,
  onChange,
  error,
  required = false,
}: DocumentUploadProps) {
  const http = useHttpClient();
  const id = useId();
  const [failure, setFailure] = useState<string | null>(null);
  const inFlight = useRef<AbortController | null>(null);

  // Si la sección se oculta (p. ej. "No" es víctima) la subida en curso se cancela.
  useEffect(
    () => () => {
      inFlight.current?.abort();
    },
    [],
  );

  async function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Permite volver a elegir el mismo archivo tras un error.
    event.target.value = '';
    if (!file) return;
    const problem = pdfProblem(file);
    if (problem) {
      setFailure(problem);
      return;
    }
    setFailure(null);
    inFlight.current?.abort();
    const controller = new AbortController();
    inFlight.current = controller;
    const previous = value;
    onChange({ status: 'uploading', name: file.name, size: file.size });
    try {
      const ticket = await requestUpload(http, kind, controller.signal);
      await putToStorage(ticket, file, controller.signal);
      if (controller.signal.aborted) return;
      onChange({ status: 'ready', s3Key: ticket.s3Key, name: file.name, size: file.size });
    } catch (cause) {
      if (controller.signal.aborted) return;
      onChange(previous.status === 'ready' ? previous : EMPTY_DOCUMENT);
      setFailure(uploadFailure(cause));
    }
  }

  const message = failure ?? error;
  const titleId = `${id}-titulo`;
  const hintId = `${id}-ayuda`;
  const statusId = `${id}-estado`;
  const errorId = `${id}-error`;
  const uploading = value.status === 'uploading';

  return (
    <div>
      <p id={titleId} className="font-semibold text-ink">
        {title}
        {required ? ' (obligatorio)' : ' (opcional)'}
      </p>
      <p id={hintId} className="mt-0.5 text-sm text-ink-muted">
        {hint}
      </p>

      <div
        id={statusId}
        aria-live="polite"
        className={`mt-2 rounded-xl border-2 px-4 py-3 ${
          message ? 'border-danger-border' : 'border-border'
        } ${value.status === 'ready' ? 'bg-success-bg' : 'bg-canvas'}`}
      >
        {value.status === 'empty' && <p className="text-ink-muted">Ningún archivo cargado.</p>}
        {value.status === 'uploading' && (
          <p className="flex items-center gap-2 font-semibold text-ink">
            <Icon name="file" className="text-brand" />
            <span className="min-w-0 break-words">Subiendo {value.name}…</span>
          </p>
        )}
        {value.status === 'ready' && (
          <p className="flex items-start gap-2 font-semibold text-success-text">
            <Icon name="check" className="mt-0.5" />
            <span className="min-w-0 break-words">
              Archivo cargado: {value.name} ({formatFileSize(value.size)})
            </span>
          </p>
        )}

        <input
          id={id}
          type="file"
          name={name}
          accept="application/pdf,.pdf"
          disabled={uploading}
          onChange={(event) => void handleFile(event)}
          aria-labelledby={`${id}-boton ${titleId}`}
          aria-describedby={[hintId, statusId, message ? errorId : null].filter(Boolean).join(' ')}
          aria-invalid={message ? true : undefined}
          className="peer sr-only"
        />
        <label
          id={`${id}-boton`}
          htmlFor={id}
          className={`mt-3 inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border-2 border-brand px-4 font-semibold text-brand peer-focus-visible:shadow-[0_0_0_6px_var(--color-focus-halo)] peer-focus-visible:outline-3 peer-focus-visible:outline-focus ${
            uploading ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:bg-brand-soft'
          }`}
        >
          <Icon name="file" />
          {value.status === 'ready' ? 'Cambiar PDF' : 'Elegir PDF'}
        </label>
      </div>

      {message && (
        <p
          id={errorId}
          className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-danger-text"
        >
          <Icon name="alert" size={18} className="mt-px" />
          {message}
        </p>
      )}
    </div>
  );
}
