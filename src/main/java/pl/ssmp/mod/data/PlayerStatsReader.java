package pl.ssmp.mod.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.model.PlayerStats;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Odczytuje statystyki gracza bezpośrednio z pliku
 * {@code <katalog_świata>/stats/<UUID>.json} na dysku.
 *
 * Wyniki są cache'owane przez {@value #CACHE_TTL_MS} ms, aby uniknąć
 * wielokrotnych odczytów z dysku przy szybkich kolejnych wywołaniach {@code /stats}.
 */
public class PlayerStatsReader {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");
    private static final long CACHE_TTL_MS = 30_000L;

    private record CachedStats(PlayerStats stats, long expiresAt) {}

    private final Path statsDir;
    private final Map<String, CachedStats> cache = new ConcurrentHashMap<>();

    /**
     * @param statsDir katalog {@code <world>/stats/} zawierający pliki <UUID>.json
     */
    public PlayerStatsReader(Path statsDir) {
        this.statsDir = statsDir;
    }

    /**
     * Zwraca statystyki gracza o podanym UUID.
     *
     * @param mcUuid UUID gracza z myślnikami (np. "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
     * @return {@link Optional#empty()} gdy plik nie istnieje lub nie da się go odczytać
     * @throws IOException gdy plik istnieje ale wystąpił błąd parsowania JSON
     */
    public Optional<PlayerStats> getStats(String mcUuid) throws IOException {
        CachedStats cached = cache.get(mcUuid);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            return Optional.ofNullable(cached.stats());
        }

        Path file = statsDir.resolve(mcUuid + ".json");
        if (!Files.exists(file)) {
            cache.put(mcUuid, new CachedStats(null, System.currentTimeMillis() + CACHE_TTL_MS));
            return Optional.empty();
        }

        PlayerStats stats = parse(file);
        cache.put(mcUuid, new CachedStats(stats, System.currentTimeMillis() + CACHE_TTL_MS));
        return Optional.of(stats);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsowanie JSON
    // ─────────────────────────────────────────────────────────────────────────

    private static PlayerStats parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root   = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject stats  = root.has("stats") ? root.getAsJsonObject("stats") : new JsonObject();

            JsonObject custom = getSection(stats, "minecraft:custom");
            JsonObject mined  = getSection(stats, "minecraft:mined");
            JsonObject killed = getSection(stats, "minecraft:killed");

            PlayerStats ps = new PlayerStats();
            ps.playTimeTicks        = getLong(custom, "minecraft:play_time");
            ps.deaths               = getInt(custom,  "minecraft:deaths");
            ps.mobKills             = getInt(custom,  "minecraft:mob_kills");
            ps.playerKills          = getInt(custom,  "minecraft:player_kills");
            ps.damageDealt          = getLong(custom, "minecraft:damage_dealt");
            ps.damageTaken          = getLong(custom, "minecraft:damage_taken");
            ps.walkCm               = getLong(custom, "minecraft:walk_one_cm");
            ps.sprintCm             = getLong(custom, "minecraft:sprint_one_cm");
            ps.swimCm               = getLong(custom, "minecraft:swim_one_cm");
            ps.flyCm                = getLong(custom, "minecraft:fly_one_cm");
            ps.jumps                = getInt(custom,  "minecraft:jump");
            ps.itemsEnchanted       = getInt(custom,  "minecraft:item_enchanted");
            ps.tradedWithVillager   = getInt(custom,  "minecraft:traded_with_villager");
            ps.timeSinceDeathTicks  = getLong(custom, "minecraft:time_since_death");

            ps.diamondOresMined     = getInt(mined, "minecraft:diamond_ore")
                                     + getInt(mined, "minecraft:deepslate_diamond_ore");
            ps.ancientDebrisMined   = getInt(mined, "minecraft:ancient_debris");
            ps.coalOresMined        = getInt(mined, "minecraft:coal_ore")
                                     + getInt(mined, "minecraft:deepslate_coal_ore");

            ps.enderDragonKills     = getInt(killed, "minecraft:ender_dragon");
            ps.witherKills          = getInt(killed, "minecraft:wither");

            return ps;
        } catch (RuntimeException e) {
            throw new IOException("Błąd parsowania pliku stats: " + file, e);
        }
    }

    private static JsonObject getSection(JsonObject stats, String key) {
        return stats.has(key) ? stats.getAsJsonObject(key) : new JsonObject();
    }

    private static long getLong(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsLong() : 0L;
    }

    private static int getInt(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsInt() : 0;
    }
}
