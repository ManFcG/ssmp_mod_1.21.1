package pl.ssmp.mod.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.data.AccountLinkStorage;
import pl.ssmp.mod.data.PendingLinksManager;
import pl.ssmp.mod.model.AccountLink;

import java.awt.Color;
import java.time.Instant;
import java.util.Optional;

/**
 * Obsługuje slash command {@code /link <kod>} na Discordzie.
 *
 * Weryfikuje 6-znakowy kod wygenerowany przez gracza w Minecrafcie (/discord link),
 * a następnie tworzy trwałe powiązanie Discord ↔ Minecraft w bazie SQLite.
 */
public class LinkCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private static final Color COLOR_SUCCESS = new Color(0x57F287);
    private static final Color COLOR_ERROR   = new Color(0xED4245);

    private LinkCommand() {}

    /**
     * Główna metoda obsługi komendy {@code /link <kod>}.
     *
     * @param event   zdarzenie slash command (deferReply powinno być wywołane wcześniej)
     * @param storage baza danych powiązań kont
     */
    public static void handle(SlashCommandInteractionEvent event, AccountLinkStorage storage) {
        String discordId = event.getUser().getId();
        String code = event.getOption("kod") != null
                ? event.getOption("kod").getAsString().trim().toUpperCase()
                : "";

        if (code.isBlank()) {
            event.getHook().sendMessageEmbeds(errorEmbed(
                    "❌ Nie podano kodu.",
                    "Wejdź do Minecrafta i wpisz `/discord link` aby wygenerować kod."
            )).queue();
            return;
        }

        // 1. Sprawdź czy Discord jest już zlinkowany
        Optional<AccountLink> existing = storage.getByDiscordId(discordId);
        if (existing.isPresent()) {
            AccountLink link = existing.get();
            event.getHook().sendMessageEmbeds(errorEmbed(
                    "⚠️ Konto już połączone.",
                    "Twoje konto Discord jest już powiązane z graczem **" + link.mcUsername() + "**.\n"
                            + "Aby odlinkować, użyj komendy `/unlink`."
            )).queue();
            return;
        }

        // 2. Pobierz i zweryfikuj kod
        PendingLinksManager.PendingLink pending = PendingLinksManager.consume(code);
        if (pending == null) {
            event.getHook().sendMessageEmbeds(errorEmbed(
                    "❌ Kod wygasł lub jest nieprawidłowy.",
                    "Wróć do Minecrafta i wpisz `/discord link` aby wygenerować nowy kod."
            )).queue();
            return;
        }

        // 3. Sprawdź czy ten UUID Minecraft nie jest już zlinkowany
        Optional<AccountLink> existingMc = storage.getByMcUuid(pending.mcUuid());
        if (existingMc.isPresent()) {
            event.getHook().sendMessageEmbeds(errorEmbed(
                    "❌ Konto Minecraft jest już powiązane.",
                    "Gracz **" + pending.mcUsername() + "** ma już powiązane inne konto Discord."
            )).queue();
            return;
        }

        // 4. Zapisz powiązanie
        long linkedAt = Instant.now().getEpochSecond();
        AccountLink link = new AccountLink(discordId, pending.mcUuid(), pending.mcUsername(), linkedAt);
        try {
            storage.insert(link);
        } catch (Exception e) {
            LOGGER.error("[SSMP] Błąd zapisu powiązania konta (discord={}): {}", discordId, e.getMessage(), e);
            event.getHook().sendMessageEmbeds(errorEmbed(
                    "⚠️ Błąd bazy danych.",
                    "Nie udało się zapisać powiązania. Spróbuj ponownie za chwilę."
            )).queue();
            return;
        }

        // 5. Sukces
        String discordTag = event.getUser().getAsTag();
        LOGGER.info("[SSMP] Powiązano konto: Discord {} ↔ Minecraft {} ({})",
                discordTag, pending.mcUsername(), pending.mcUuid());
        event.getHook().sendMessageEmbeds(successEmbed(link, discordTag)).queue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Embedy
    // ─────────────────────────────────────────────────────────────────────────

    private static MessageEmbed successEmbed(AccountLink link, String discordTag) {
        Instant linkedInstant = Instant.ofEpochSecond(link.linkedAt());
        return new EmbedBuilder()
                .setTitle("✅ Konto połączone!")
                .setColor(COLOR_SUCCESS)
                .addField("Minecraft", link.mcUsername(), false)
                .setTimestamp(linkedInstant)
                .setFooter("Połączono")
                .build();
    }

    static MessageEmbed errorEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(COLOR_ERROR)
                .build();
    }
}
