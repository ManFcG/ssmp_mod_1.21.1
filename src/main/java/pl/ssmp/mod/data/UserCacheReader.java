package pl.ssmp.mod.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Odczytuje plik {@code usercache.json} serwera Minecraft, który przechowuje
 * mapowanie: nick gracza → UUID.
 *
 * Plik jest cache'owany w pamięci z TTL {@value #CACHE_TTL_MS} ms,
 * aby uniknąć wielokrotnych odczytów z dysku przy każdym wywołaniu {@code /stats}.
 */
public class UserCacheReader {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");
    private static final long CACHE_TTL_MS = 60_000L;

    private record Entry(String name, String uuid) {}

    private final Path usercachePath;
    private List<Entry> cache    = Collections.emptyList();
    private long        cacheExp = 0L;

    /**
     * @param serverDir katalog główny serwera (tam gdzie leży {@code usercache.json})
     */
    public UserCacheReader(Path serverDir) {
        this.usercachePath = serverDir.resolve("usercache.json");
    }

    /**
     * Zwraca UUID gracza o podanej nazwie (case-insensitive).
     * Odczytuje plik z dysku co najwyżej raz na {@value #CACHE_TTL_MS} ms.
     */
    public Optional<String> getUuidByName(String name) {
        refresh();
        return cache.stream()
                .filter(e -> e.name().equalsIgnoreCase(name))
                .map(Entry::uuid)
                .findFirst();
    }

    /**
     * Wymuś odświeżenie cache przy następnym wywołaniu.
     */
    public void invalidate() {
        cacheExp = 0L;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    private void refresh() {
        if (System.currentTimeMillis() < cacheExp) return;
        if (!Files.exists(usercachePath)) {
            cache    = Collections.emptyList();
            cacheExp = System.currentTimeMillis() + CACHE_TTL_MS;
            return;
        }
        try (Reader reader = Files.newBufferedReader(usercachePath)) {
            JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
            List<Entry> loaded = new java.util.ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                var obj = el.getAsJsonObject();
                String n = obj.has("name") ? obj.get("name").getAsString() : null;
                String u = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
                if (n != null && u != null) {
                    loaded.add(new Entry(n, u));
                }
            }
            cache    = Collections.unmodifiableList(loaded);
            cacheExp = System.currentTimeMillis() + CACHE_TTL_MS;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[SSMP] Nie udało się wczytać usercache.json: {}", e.getMessage());
            cache    = Collections.emptyList();
            cacheExp = System.currentTimeMillis() + CACHE_TTL_MS;
        }
    }
}
