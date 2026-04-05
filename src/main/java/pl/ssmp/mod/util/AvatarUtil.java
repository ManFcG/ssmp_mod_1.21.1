package pl.ssmp.mod.util;

/**
 * Narzędzia pomocnicze do avatarów graczy Minecraft.
 * Używa publicznych API do pobierania awatarów na podstawie nazwy/UUID gracza.
 */
public final class AvatarUtil {

    // Minotar – głowa gracza (64×64)
    private static final String HEAD_URL = "https://minotar.net/avatar/%s/64";
    // mc-heads.net – pełna renderka (opcjonalnie)
    private static final String BODY_URL  = "https://mc-heads.net/body/%s/128";

    private AvatarUtil() {}

    /**
     * Zwraca URL do 64×64 awatara (głowy) gracza.
     *
     * @param nameOrUuid nazwa gracza lub UUID (bez myślników)
     */
    public static String getHeadUrl(String nameOrUuid) {
        return String.format(HEAD_URL, nameOrUuid);
    }

    /**
     * Zwraca URL do renderki pełnego ciała gracza.
     *
     * @param nameOrUuid nazwa gracza lub UUID (bez myślników)
     */
    public static String getBodyUrl(String nameOrUuid) {
        return String.format(BODY_URL, nameOrUuid);
    }
}
