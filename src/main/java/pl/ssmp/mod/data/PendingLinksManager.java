package pl.ssmp.mod.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Zarządza tymczasowymi kodami linkowania kont Discord ↔ Minecraft.
 *
 * Kody są przechowywane wyłącznie w pamięci (RAM).
 * Każdy kod wygasa automatycznie po {@value #EXPIRY_MINUTES} minutach.
 */
public class PendingLinksManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");
    private static final int    EXPIRY_MINUTES = 1;
    private static final String CODE_CHARS     = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int    CODE_LENGTH    = 6;

    public record PendingLink(String mcUuid, String mcUsername, long expiresAt) {}

    private static final Map<String, PendingLink>    pendingCodes = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService    scheduler    =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ssmp-link-expiry");
                t.setDaemon(true);
                return t;
            });
    private static final SecureRandom rng = new SecureRandom();

    private PendingLinksManager() {}

    /**
     * Generuje nowy kod dla gracza. Jeśli gracz miał już oczekujący kod, zostaje usunięty.
     *
     * @param mcUuid     UUID gracza Minecraft
     * @param mcUsername Nazwa gracza Minecraft
     * @return wygenerowany 6-znakowy kod (wielkie litery + cyfry)
     */
    public static String generateCode(String mcUuid, String mcUsername) {
        // Usuń stare kody tego gracza
        pendingCodes.entrySet().removeIf(e -> e.getValue().mcUuid().equals(mcUuid));

        String code = randomCode();
        long expiresAt = System.currentTimeMillis() + EXPIRY_MINUTES * 60_000L;
        pendingCodes.put(code, new PendingLink(mcUuid, mcUsername, expiresAt));

        // Automatyczne wygaśnięcie po 1 minucie
        scheduler.schedule(() -> pendingCodes.remove(code), EXPIRY_MINUTES, TimeUnit.MINUTES);

        LOGGER.debug("[SSMP] Wygenerowano kod linkowania {} dla gracza {}", code, mcUsername);
        return code;
    }

    /**
     * Pobiera i usuwa oczekujące powiązanie dla podanego kodu.
     * Zwraca {@code null} jeśli kod nie istnieje lub wygasł.
     *
     * @param code kod wpisany przez użytkownika (case-insensitive)
     */
    public static PendingLink consume(String code) {
        if (code == null || code.isBlank()) return null;
        String upper = code.trim().toUpperCase();
        PendingLink link = pendingCodes.get(upper);
        if (link == null) return null;
        if (System.currentTimeMillis() > link.expiresAt()) {
            pendingCodes.remove(upper);
            return null;
        }
        pendingCodes.remove(upper);
        return link;
    }

    /**
     * Sprawdza czy gracz ma aktywny oczekujący kod (jeszcze nie wygasł).
     *
     * @param mcUuid UUID gracza
     * @return kod lub {@code null} jeśli brak aktywnego kodu
     */
    public static String getActiveCode(String mcUuid) {
        long now = System.currentTimeMillis();
        return pendingCodes.entrySet().stream()
                .filter(e -> e.getValue().mcUuid().equals(mcUuid) && now <= e.getValue().expiresAt())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** Zatrzymuje wątek automatycznego wygasania (wywołaj przy zamykaniu serwera). */
    public static void shutdown() {
        scheduler.shutdownNow();
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(rng.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
