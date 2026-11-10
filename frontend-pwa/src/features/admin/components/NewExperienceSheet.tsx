import { useId, useState } from 'react';

import { ApiError } from '@/shared/api';
import { BottomSheet, Icon, ProblemNotice, Select } from '@/shared/ui';

import { experienceServerErrors } from '../forms';
import { useCreateAdminExperience, useHostOptions } from '../queries';
import { ExperienceForm } from './ExperienceForm';

interface NewExperienceSheetProps {
  readonly onClose: () => void;
  /** Recibe el título de la experiencia creada. */
  readonly onCreated: (title: string) => void;
}

/** Alta de una experiencia por el administrador a nombre de un anfitrión (ADR-0048 §2). */
export function NewExperienceSheet({ onClose, onCreated }: NewExperienceSheetProps) {
  const hosts = useHostOptions();
  const create = useCreateAdminExperience();
  const helpId = useId();
  const [hostId, setHostId] = useState('');
  const [hostError, setHostError] = useState<string | null>(null);

  const options = (hosts.data?.items ?? []).map((host) => ({
    value: host.hostProfileId,
    label: host.associationName ? `${host.fullName} · ${host.associationName}` : host.fullName,
  }));

  return (
    <BottomSheet open onClose={onClose} title="Nueva experiencia">
      <div className="grid gap-5">
        {hosts.isError ? (
          <ProblemNotice
            error={hosts.error}
            onRetry={() => {
              void hosts.refetch();
            }}
          />
        ) : (
          <div>
            <Select
              label="Anfitrión"
              name="hostProfileId"
              size="lg"
              searchable
              searchPlaceholder="Escribe para buscar al anfitrión…"
              value={hostId}
              options={options}
              disabled={hosts.isPending}
              invalid={hostError !== null}
              describedBy={hostError ? `${helpId}-error` : undefined}
              onChange={(value) => {
                setHostId(value);
                setHostError(null);
              }}
            />
            {hostError && (
              <p
                id={`${helpId}-error`}
                className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-danger-text"
              >
                <Icon name="alert" size={18} className="mt-px" />
                {hostError}
              </p>
            )}
            {hosts.data?.items.length === 0 && (
              <p className="mt-1 text-sm text-ink-muted">
                Aún no hay anfitriones. Conviértelos desde Personas.
              </p>
            )}
          </div>
        )}
        <ExperienceForm
          initial={null}
          ariaLabel="Datos de la nueva experiencia"
          submitLabel="Crear experiencia"
          busyLabel="Creando…"
          pending={create.isPending}
          error={create.error}
          beforeSubmit={() => {
            if (hostId === '') {
              setHostError('Elige el anfitrión de la experiencia.');
              return false;
            }
            return true;
          }}
          onSubmit={(experience, fail) => {
            create.mutate(
              { hostProfileId: hostId, experience },
              {
                onSuccess: (created) => {
                  onCreated(created.title);
                },
                onError: (error) => {
                  if (error instanceof ApiError && error.invalidParams.length > 0) {
                    fail(experienceServerErrors(error.invalidParams));
                  }
                },
              },
            );
          }}
        />
      </div>
    </BottomSheet>
  );
}
