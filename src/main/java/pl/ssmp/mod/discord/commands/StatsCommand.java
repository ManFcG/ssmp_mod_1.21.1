package pl.ssmp.mod.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.DiscordBridge;
import pl.ssmp.mod.data.AccountLinkStorage;
import pl.ssmp.mod.data.PlayerStatsReader;
import pl.ssmp.mod.data.UserCacheReader;
import pl.ssmp.mod.discord.embeds.StatsEmbedBuilder;
import pl.ssmp.mod.model.AccountLink;
import pl.ssmp.mod.model.PlayerStats;

import java.awt.Color;
import java.io.IOException;
import java.util.Optional;

/**
 * Obsługuje slash command {@code /stats} na Discordzie.
 *
 * Użycie:
 * <ul>
 *   <li>{@code /stats}                   – statystyki wywołującego (musi być zlinkowany)</li>
 *   <li>{@code /stats uzytkownik:@user}  – statystyki innego użytkownika Discord</li>
 *   <li>{@code /stats nick:Steve}        – statystyki gracza MC po nicku</li>
 * </ul>
 */
public class StatsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");
    private static final Color  COLOR_ERROR = new Color(0xED4245);

    private StatsCommand() {}

    /**
     * Główna metoda obsługi komendy {@code /stats}.
     *
     * @param event  zdarzenie slash command (deferReply powinno być wywołane wcześniej)
     * @param bridge instancja DiscordBridge (dostęp do storage i serwera MC)
     */
    public static void handle(SlashCommandInteractionEvent event, DiscordBridge bridge) {
        AccountLinkStorage storage      = bridge.getAccountLinkStorage();
        PlayerStatsReader  statsReader  = bridge.getPlayerStatsReader();
        UserCacheReader    cacheReader  = bridge.getUserCacheReader();
        MinecraftServer    server       = bridge.getServer();

        if (statsReader == null) {
            event.getHook().sendMessageEmbeds(serviceUnavailableEmbed()).queue();
            return;
        }

        OptionMapping userOpt = event.getOption("uzytkownik");
        OptionMapping nickOpt = event.getOption("nick");

        try {
            if (userOpt != null) {
                // /stats @user
                handleByDiscordUser(event, userOpt.getAsUser(), storage, statsReader, server);
            } else if (nickOpt != null) {
                // /stats nick:Steve
                handleByMcNick(event, nickOpt.getAsString().trim(), storage, statsReader, cacheReader, server);
            } else {
                // /stats (własne)
                handleByDiscordUser(event, event.getUser(), storage, statsReader, server);
            }
        } catch (IOException e) {
            LOGGER.warn("[SSMP] Błąd odczytu statystyk: {}", e.getMessage());
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("⚠️ Nie udało się odczytać statystyk.")
                    .setDescription("Spróbuj za chwilę.")
                    .setColor(COLOR_ERROR).build()
            ).queue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pomocnicze metody
    // ─────────────────────────────────────────────────────────────────────────

    private static void handleByDiscordUser(SlashCommandInteractionEvent event,
                                             User discordUser,
                                             AccountLinkStorage storage,
                                             PlayerStatsReader statsReader,
                                             MinecraftServer server) throws IOException {
        Optional<AccountLink> link = storage.getByDiscordId(discordUser.getId());
        if (link.isEmpty()) {
            String desc = discordUser.getId().equals(event.getUser().getId())
                    ? "Twoje konto Discord nie jest powiązane z Minecraftem.\n"
                            + "Wpisz `/discord link` w grze aby to zrobić."
                    : "Użytkownik **@" + discordUser.getEffectiveName() + "** nie ma powiązanego konta Minecraft.";
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("❌ Konto nie jest połączone.")
                    .setDescription(desc)
                    .setColor(COLOR_ERROR).build()
            ).queue();
            return;
        }

        AccountLink al = link.get();
        sendStatsEmbed(event, al.mcUsername(), al.mcUuid(), statsReader, server);
    }

    private static void handleByMcNick(SlashCommandInteractionEvent event,
                                        String nick,
                                        AccountLinkStorage storage,
                                        PlayerStatsReader statsReader,
                                        UserCacheReader cacheReader,
                                        MinecraftServer server) throws IOException {
        // 1. Sprawdź w bazie powiązań
        Optional<AccountLink> link = storage.getByMcUsername(nick);
        if (link.isPresent()) {
            AccountLink al = link.get();
            sendStatsEmbed(event, al.mcUsername(), al.mcUuid(), statsReader, server);
            return;
        }

        // 2. Sprawdź w usercache.json
        if (cacheReader != null) {
            Optional<String> uuid = cacheReader.getUuidByName(nick);
            if (uuid.isPresent()) {
                sendStatsEmbed(event, nick, uuid.get(), statsReader, server);
                return;
            }
        }

        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("❌ Nie znaleziono gracza.")
                .setDescription("Nie znaleziono gracza o nicku **" + nick + "**.\n"
                        + "Upewnij się że nick jest poprawny i gracz grał na tym serwerze.")
                .setColor(COLOR_ERROR).build()
        ).queue();
    }

    private static void sendStatsEmbed(SlashCommandInteractionEvent event,
                                        String mcUsername,
                                        String mcUuid,
                                        PlayerStatsReader statsReader,
                                        MinecraftServer server) throws IOException {
        Optional<PlayerStats> statsOpt = statsReader.getStats(mcUuid);

        if (statsOpt.isEmpty()) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("ℹ️ Brak danych")
                    .setDescription("Gracz **" + mcUsername + "** jeszcze nie grał na serwerze.")
                    .setColor(new Color(0x5865F2)).build()
            ).queue();
            return;
        }

        boolean online = server != null && server.getPlayerManager().getPlayerList().stream()
                .map(ServerPlayerEntity::getName)
                .map(t -> t.getString())
                .anyMatch(mcUsername::equals);

        MessageEmbed embed = StatsEmbedBuilder.build(mcUsername, mcUuid, statsOpt.get(), online);
        event.getHook().sendMessageEmbeds(embed).queue();
    }

    private static MessageEmbed serviceUnavailableEmbed() {
        return new EmbedBuilder()
                .setTitle("⚠️ Serwer niedostępny.")
                .setDescription("Statystyki są dostępne dopiero po uruchomieniu serwera Minecraft.")
                .setColor(COLOR_ERROR).build();
    }
}
