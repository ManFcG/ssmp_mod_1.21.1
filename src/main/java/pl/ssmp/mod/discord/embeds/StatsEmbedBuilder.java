package pl.ssmp.mod.discord.embeds;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import pl.ssmp.mod.model.PlayerStats;
import pl.ssmp.mod.util.AvatarUtil;

import java.awt.Color;
import java.time.Instant;

/**
 * Buduje embed Discord ze statystykami gracza Minecraft.
 */
public class StatsEmbedBuilder {

    private static final Color COLOR_ONLINE  = new Color(0x00AA44);
    private static final Color COLOR_OFFLINE = new Color(0x888888);
    private static final String SPACER_FIELD = "\u200B";

    private StatsEmbedBuilder() {}

    /**
     * Buduje pełny embed ze statystykami gracza.
     *
     * @param mcUsername nazwa gracza
     * @param mcUuid     UUID gracza
     * @param stats      statystyki odczytane z pliku
     * @param online     czy gracz jest aktualnie online
     */
    public static MessageEmbed build(String mcUsername, String mcUuid, PlayerStats stats, boolean online) {
        Color color = online ? COLOR_ONLINE : COLOR_OFFLINE;
        String onlineStatus = online ? "Jest na serwerze" : "Nie jest na serwerze";

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📊 Statystyki gracza: " + mcUsername)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setThumbnail(AvatarUtil.getHeadUrl(mcUsername))
                .setFooter(onlineStatus);

        // Wiersz 1: Czas gry i śmierci
        eb.addField("⏱️ Czas gry",       formatTicks(stats.playTimeTicks), true);
        eb.addField("💀 Śmierci",         String.valueOf(stats.deaths),     true);
        eb.addField(SPACER_FIELD, SPACER_FIELD, true); // spacer dla 3-kolumnowego układu

        // Wiersz 2: Zabójstwa
        eb.addField("⚔️ Zabite moby",     String.valueOf(stats.mobKills),     true);
        eb.addField("🗡️ Zabici gracze",   String.valueOf(stats.playerKills),  true);
        eb.addField(SPACER_FIELD, SPACER_FIELD, true);

        // Wiersz 3: Obrażenia
        eb.addField("❤️‍🔥 Zadane obrażenia",    formatDamage(stats.damageDealt), true);
        eb.addField("🛡️ Otrzymane obrażenia",   formatDamage(stats.damageTaken), true);
        eb.addField(SPACER_FIELD, SPACER_FIELD, true);

        // Wiersz 4: Dystans i enchanty
        long totalMoveCm = stats.walkCm + stats.sprintCm;
        eb.addField("🚶 Pokonany dystans", formatDistance(totalMoveCm), true);
        eb.addField("✨ Enchantowane",     String.valueOf(stats.itemsEnchanted), true);
        eb.addField(SPACER_FIELD, SPACER_FIELD, true);

        // Wiersz 5: Dodatkowe statystyki
        eb.addField("💎 Diamenty",         String.valueOf(stats.diamondOresMined), true);
        eb.addField("🔥 Ancient Debris",   String.valueOf(stats.ancientDebrisMined), true);

        return eb.build();
    }

    /**
     * Buduje uproszczony embed pokazujący wyłącznie czas gry.
     */
    public static MessageEmbed buildPlaytime(String mcUsername, String mcUuid, PlayerStats stats, boolean online) {
        Color color = online ? COLOR_ONLINE : COLOR_OFFLINE;
        String onlineStatus = online ? "Jest na serwerze" : "Nie jest na serwerze";

        return new EmbedBuilder()
                .setTitle("⏱️ Czas gry: " + mcUsername)
                .addField("⏱️ Czas gry",   formatTicks(stats.playTimeTicks), true)
                .addField("💀 Śmierci",    String.valueOf(stats.deaths),     true)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setThumbnail(AvatarUtil.getHeadUrl(mcUsername))
                .setFooter(onlineStatus)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Formatowanie
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Formatuje wartość w tickach (20 tps) do postaci „Xh Ym" lub „Xm" lub „Xs".
     */
    public static String formatTicks(long ticks) {
        long seconds  = ticks / 20;
        long minutes  = seconds / 60;
        long hours    = minutes / 60;
        long remMin   = minutes % 60;

        if (hours > 0)   return hours + "h " + remMin + "m";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    /**
     * Formatuje wartość obrażeń (w 0.1 HP) do postaci np. „1523.4 HP".
     */
    private static String formatDamage(long rawDamage) {
        double hp = rawDamage / 10.0;
        return String.format("%.1f HP", hp);
    }

    /**
     * Formatuje odległość w centymetrach gry do km z jednym miejscem po przecinku.
     */
    private static String formatDistance(long cm) {
        double km = cm / 100_000.0;
        return String.format("%.1f km", km);
    }
}
