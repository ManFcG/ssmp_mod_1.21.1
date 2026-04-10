package pl.ssmp.mod.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.config.ModConfig;
import pl.ssmp.mod.data.AccountLinkStorage;
import pl.ssmp.mod.model.AccountLink;

import java.awt.Color;
import java.util.Optional;

/**
 * Obsługuje slash command {@code /whois @użytkownik} na Discordzie.
 *
 * Pokazuje nick Minecraft powiązany z danym kontem Discord.
 * Dostępne dla adminów lub dla sprawdzania własnego konta.
 */
public class WhoisCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private static final Color COLOR_INFO  = new Color(0x5865F2);
    private static final Color COLOR_ERROR = new Color(0xED4245);

    private WhoisCommand() {}

    /**
     * Główna metoda obsługi komendy {@code /whois @użytkownik}.
     *
     * @param event   zdarzenie slash command (deferReply powinno być wywołane wcześniej)
     * @param storage baza danych powiązań kont
     * @param config  konfiguracja moda (do sprawdzania adminów)
     */
    public static void handle(SlashCommandInteractionEvent event,
                               AccountLinkStorage storage,
                               ModConfig config) {
        String requesterId = event.getUser().getId();

        OptionMapping userOpt = event.getOption("uzytkownik");
        if (userOpt == null) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("❌ Nie podano użytkownika.")
                    .setDescription("Podaj użytkownika Discord: `/whois @użytkownik`")
                    .setColor(COLOR_ERROR).build()
            ).queue();
            return;
        }

        User targetUser = userOpt.getAsUser();
        String targetId = targetUser.getId();

        // Sprawdź uprawnienia: admin może sprawdzić każdego, inni tylko siebie
        if (!config.isAdminUser(requesterId) && !requesterId.equals(targetId)) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("❌ Brak uprawnień.")
                    .setDescription("Możesz sprawdzić wyłącznie swoje własne konto.")
                    .setColor(COLOR_ERROR).build()
            ).queue();
            return;
        }

        Optional<AccountLink> link = storage.getByDiscordId(targetId);
        if (link.isEmpty()) {
            String desc = targetId.equals(requesterId)
                    ? "Twoje konto Discord nie jest powiązane z żadnym kontem Minecraft.\n"
                            + "Wpisz `/discord link` w grze aby to zrobić."
                    : "Użytkownik **@" + targetUser.getEffectiveName() + "** nie ma powiązanego konta Minecraft.";
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("⚠️ Brak powiązania.")
                    .setDescription(desc)
                    .setColor(COLOR_ERROR).build()
            ).queue();
            return;
        }

        AccountLink al = link.get();
        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("🔍 Wynik wyszukiwania")
                .setColor(COLOR_INFO)
                .addField("💬 Discord",     "@" + targetUser.getEffectiveName(), true)
                .addField("⛏️ Minecraft",    al.mcUsername(), true)
                .addField("🆔 UUID",         "`" + al.mcUuid() + "`", false)
                .setTimestamp(java.time.Instant.ofEpochSecond(al.linkedAt()))
                .setFooter("Połączono")
                .build()
        ).queue();
    }
}
