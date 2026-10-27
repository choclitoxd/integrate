package com.integrate.modules.catalog.meetingpoint;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Normalización y similitud de textos de lugares (ADR-0030 §1). Funciones puras. */
public final class MeetingPointText {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    /** Palabras que no distinguen un lugar de otro. */
    private static final Set<String> STOP_WORDS = Set.of("de", "del", "la", "las", "el", "los", "en", "y", "a", "al",
            "con", "por", "para", "un", "una");

    private MeetingPointText() {
    }

    /** Minúsculas, sin tildes ni signos y con espacios simples. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String plain = DIACRITICS.matcher(decomposed).replaceAll("");
        return NON_ALNUM.matcher(plain).replaceAll(" ").strip();
    }

    /** Términos distintivos: normalizados y sin palabras vacías. */
    public static Set<String> terms(String text) {
        Set<String> terms = new HashSet<>();
        for (String word : normalize(text).split(" ")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) {
                terms.add(word);
            }
        }
        return terms;
    }

    /** Jaccard entre los términos de dos textos: 0 sin nada en común, 1 si son iguales. Vacío contra vacío: 0. */
    public static double jaccard(String a, String b) {
        Set<String> left = terms(a);
        Set<String> right = terms(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    /** Quita del texto los términos de otro (p. ej. el municipio) para comparar solo el nombre del lugar. */
    public static String withoutTerms(String text, String toRemove) {
        Set<String> remove = terms(toRemove);
        StringBuilder out = new StringBuilder();
        for (String word : normalize(text).split(" ")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word) && !remove.contains(word)) {
                out.append(out.isEmpty() ? "" : " ").append(word);
            }
        }
        return out.toString();
    }
}
