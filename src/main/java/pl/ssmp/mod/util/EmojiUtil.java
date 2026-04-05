package pl.ssmp.mod.util;

/**
 * Narzędzia do konwersji emoji Unicode ↔ tekst.
 *
 * Konwertuje znaki emoji na format :nazwa: używając nazw Unicode zwracanych przez
 * {@link Character#getName(int)}, np. 😃 → :smiling_face_with_open_mouth:
 */
public final class EmojiUtil {

    private EmojiUtil() {}

    /**
     * Konwertuje wszystkie emoji Unicode w tekście na format :nazwa:.
     *
     * @param text tekst wejściowy
     * @return tekst z emoji zastąpionymi przez :nazwa:
     */
    public static String unicodeToText(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (isEmoji(cp)) {
                String name = safeGetName(cp);
                if (name != null) {
                    result.append(':').append(name.toLowerCase().replace(' ', '_')).append(':');
                } else {
                    result.appendCodePoint(cp);
                }
            } else {
                result.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return result.toString();
    }

    /**
     * Sprawdza czy dany kod znaku to emoji.
     * Obejmuje główne bloki emoji Unicode.
     */
    private static boolean isEmoji(int cp) {
        return (cp >= 0x1F600 && cp <= 0x1F64F)  // Emoticons
            || (cp >= 0x1F300 && cp <= 0x1F5FF)  // Misc Symbols & Pictographs
            || (cp >= 0x1F680 && cp <= 0x1F6FF)  // Transport & Map
            || (cp >= 0x1F700 && cp <= 0x1F77F)  // Alchemical Symbols
            || (cp >= 0x1F780 && cp <= 0x1F7FF)  // Geometric Shapes Extended
            || (cp >= 0x1F800 && cp <= 0x1F8FF)  // Supplemental Arrows-C
            || (cp >= 0x1F900 && cp <= 0x1F9FF)  // Supplemental Symbols
            || (cp >= 0x1FA00 && cp <= 0x1FA6F)  // Chess Symbols
            || (cp >= 0x1FA70 && cp <= 0x1FAFF)  // Symbols & Pictographs Extended-A
            || (cp >= 0x2600  && cp <= 0x26FF)   // Misc Symbols
            || (cp >= 0x2700  && cp <= 0x27BF)   // Dingbats
            || (cp >= 0xFE00  && cp <= 0xFE0F)   // Variation Selectors
            || (cp >= 0x1F1E0 && cp <= 0x1F1FF); // Regional indicator symbols (flags)
    }

    private static String safeGetName(int cp) {
        try {
            return Character.getName(cp);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Usuwa niewidoczne znaki (variation selectors, zero-width joiners) z tekstu.
     */
    public static String stripInvisible(String text) {
        if (text == null) return null;
        return text.codePoints()
                .filter(cp -> cp != 0xFE0F && cp != 0xFE0E && cp != 0x200D)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
