package pl.ssmp.mod.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.data.AccountLinkStorage;
import pl.ssmp.mod.model.AccountLink;

import java.awt.Color;
import java.util.Optional;

/**
 * Obsługuje slash command {@code /unlink} na Discordzie.
 *
 * Usuwa powiązanie Discord ↔ Minecraft dla wywołującego użytkownika.
 */
public class UnlinkCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private static final Color COLOR_SUCCESS = new Color(0x57F287);
    private static final Color COLOR_ERROR   = new Color(0xED4245);

    private UnlinkCommand() {}

    /**
     * Główna metoda obsługi komendy {@code /unlink}.
     *
     * @param event   zdarzenie slash command (deferReply powinno być wywołane wcześniej)
     * @param storage baza danych powiązań kont
     */
    public static void handle(SlashCommandInteractionEvent event, AccountLinkStorage storage) {
        String discordId = event.getUser().getId();

        Optional<AccountLink> existing = storage.getByDiscordId(discordId);
        if (existing.isEmpty()) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("⚠️ Konto nie jest połączone.")
                    .setDescription("Twoje konto Discord nie jest powiązane z żadnym kontem Minecraft.\n"
                            + "Aby to zrobić, wejdź do Minecrafta i wpisz `/discord link`.")
                    .setColor(COLOR_ERROR)
                    .build()
            ).queue();
            return;
        }

        AccountLink link = existing.get();
        try {
            storage.deleteByDiscordId(discordId);
        } catch (Exception e) {
            LOGGER.error("[SSMP] Błąd usuwania powiązania konta (discord={}): {}", discordId, e.getMessage(), e);
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("⚠️ Błąd bazy danych.")
                    .setDescription("Nie udało się usunąć powiązania. Spróbuj ponownie za chwilę.")
                    .setColor(COLOR_ERROR)
                    .build()
            ).queue();
            return;
        }

        LOGGER.info("[SSMP] Odlinkowano konto: Discord {} ↔ Minecraft {} ({})",
                event.getUser().getAsTag(), link.mcUsername(), link.mcUuid());

        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("✅ Konto odłączone.")
                .setDescription("Powiązanie z graczem **" + link.mcUsername() + "** zostało usunięte.\n"
                        + "Możesz ponownie połączyć konto wpisując `/discord link` w Minecrafcie.")
                .setColor(COLOR_SUCCESS)
                .build()
        ).queue();
    }

    // Helper współdzielony z innymi komendami linkowania
    static MessageEmbed notLinkedEmbed() {
        return new EmbedBuilder()
                .setTitle("❌ Konto nie jest połączone.")
                .setDescription("Twoje konto Discord nie jest powiązane z żadnym kontem Minecraft.\n"
                        + "Wejdź do Minecrafta i wpisz `/discord link` aby wygenerować kod.")
                .setColor(COLOR_ERROR)
                .build();
    }
}
