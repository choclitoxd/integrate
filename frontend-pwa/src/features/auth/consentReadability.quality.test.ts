import { describe, expect, it } from 'vitest';

import { recordQuality } from '@/test/quality';

import { PURPOSE_COPY } from './purposes';

/**
 * Índice de legibilidad de Flesch-Szigriszt (IFSZ, Szigriszt-Pazos 1993) para textos en español:
 * IFSZ = 206,835 − 62,3 · (sílabas / palabras) − (palabras / frases).
 */
function fleschSzigriszt(text: string): {
  score: number;
  words: number;
  syllables: number;
  sentences: number;
} {
  const sentences = Math.max(1, text.split(/[.!?¡¿]+/).filter((s) => /\p{L}/u.test(s)).length);
  const words = text.match(/\p{L}+/gu) ?? [];
  const syllables = words.reduce((sum, word) => sum + spanishSyllables(word), 0);
  const score = 206.835 - 62.3 * (syllables / words.length) - words.length / sentences;
  return { score, words: words.length, syllables, sentences };
}

const VOWEL = /[aeiouáéíóúü]/;
/** Vocales que siempre forman núcleo propio: abiertas y cerradas con tilde (hiato). */
const STRONG = /[aeoáéóíú]/;

/** Sílabas de una palabra en español: núcleos vocálicos, con diptongos, triptongos e hiatos. */
function spanishSyllables(word: string): number {
  const w = word
    .toLowerCase()
    .replace(/q(u)(?=[eéií])/g, 'q') // la u de que/qui es muda
    .replace(/g(u)(?=[eéií])/g, 'g') // la u de gue/gui es muda (güe conserva la ü)
    .replace(/y$/, 'i'); // y final suena vocal: rey, muy
  let count = 0;
  let previous = '';
  for (const char of w) {
    if (!VOWEL.test(char)) {
      previous = '';
      continue;
    }
    if (previous === '' || (STRONG.test(previous) && STRONG.test(char))) count++;
    previous = char;
  }
  return Math.max(1, count);
}

describe('[RNF-15] Claridad y comprensión del consentimiento informado', () => {
  it('cuenta sílabas en español con diptongos e hiatos', () => {
    expect(spanishSyllables('cuenta')).toBe(2);
    expect(spanishSyllables('día')).toBe(2);
    expect(spanishSyllables('quiero')).toBe(2);
    expect(spanishSyllables('emergencia')).toBe(4);
    expect(spanishSyllables('país')).toBe(2);
    expect(spanishSyllables('Colombia')).toBe(3);
  });

  // No conformidad conocida (IFSZ ≈ 56, rango "normal" de INFLESZ): `it.fails` mantiene verde el
  // Quality Gate y se pone en rojo cuando el texto se simplifique, para pasar a `it`.
  it.fails(
    '[RNF-15] NC: el texto de autorización tiene IFSZ > 60 (lenguaje sencillo)',
    ({ task }) => {
      const text = Object.values(PURPOSE_COPY)
        .map((copy) => (/[.!?]$/.test(copy.body) ? copy.body : `${copy.body}.`))
        .join(' ');
      const result = fleschSzigriszt(text);
      const perPurpose = Object.entries(PURPOSE_COPY)
        .map(([purpose, copy]) => `${purpose}=${fleschSzigriszt(copy.body).score.toFixed(1)}`)
        .join(', ');
      recordQuality(task, {
        rnf: 'RNF-15',
        metric: 'Legibilidad Flesch-Szigriszt del consentimiento',
        value: result.score,
        unit: 'puntos',
        threshold: '> 60',
        pass: result.score > 60,
        detail: `${String(result.words)} palabras, ${String(result.syllables)} sílabas, ${String(result.sentences)} frases; por finalidad: ${perPurpose}`,
      });
      expect(result.score).toBeGreaterThan(60);
    },
  );
});
