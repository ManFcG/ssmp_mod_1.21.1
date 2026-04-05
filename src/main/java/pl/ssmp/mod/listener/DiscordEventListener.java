package pl.ssmp.mod.listener;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.DiscordBridge;
import pl.ssmp.mod.config.ModConfig;
import pl.ssmp.mod.util.EmojiUtil;

import java.util.List;

/**
 * Nasłuchuje zdarzeń Discord i przekazuje je do serwera Minecraft.
 *
 * Obsługiwane zdarzenia Discord → Minecraft:
 * - Nowa wiadomość
 * - Wiadomość w odpowiedzi
 * - Edycja wiadomości
 * - Dodanie/usunięcie reakcji
 * - Wiadomość z naklejkami
 * - Wiadomość z załącznikami
 */
public class DiscordEventListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private final DiscordBridge bridge;
    private final ModConfig config;

    public DiscordEventListener(DiscordBridge bridge, ModConfig config) {
        this.bridge = bridge;
        this.config = config;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        LOGGER.info("[SSMP] Zalogowano na Discord jako: {}", event.getJDA().getSelfUser().getAsTag());
        bridge.onReady();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nowa wiadomość
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!config.isRelayDiscordToMinecraft()) return;
        if (event.getAuthor().isBot()) return;
        if (!isTrackedChannel(event.getChannel().getId())) return;

        User author  = event.getAuthor();
        Message msg  = event.getMessage();
        String name  = author.getEffectiveName();

        // Sprawdź czy to odpowiedź
        Message referenced = msg.getReferencedMessage();

        StringBuilder content = new StringBuilder();

        // Odpowiedź
        if (referenced != null) {
            String refAuthor = referenced.getAuthor().getEffectiveName();
            String refSnip   = truncate(referenced.getContentDisplay(), 40);
            content.append("(odpowiedź na ").append(refAuthor).append(": \"").append(refSnip).append("\") ");
        }

        // Treść wiadomości
        String rawContent = msg.getContentDisplay();
        if (!rawContent.isBlank()) {
            content.append(EmojiUtil.unicodeToText(rawContent));
        }

        // Naklejki
        List<StickerItem> stickers = msg.getStickers();
        if (!stickers.isEmpty()) {
            for (StickerItem sticker : stickers) {
                content.append(" [Naklejka: ").append(sticker.getName()).append("]");
            }
        }

        // Załączniki
        List<Message.Attachment> attachments = msg.getAttachments();
        if (!attachments.isEmpty()) {
            for (Message.Attachment attachment : attachments) {
                content.append(" [Załącznik: ").append(attachment.getFileName()).append("]");
            }
        }

        if (content.isEmpty()) return;

        Text formatted = buildDiscordMessage(name, content.toString(), null);
        bridge.relayToMinecraft(formatted);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edycja wiadomości
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!config.isRelayDiscordToMinecraft()) return;
        if (event.getAuthor().isBot()) return;
        if (!isTrackedChannel(event.getChannel().getId())) return;

        String name    = event.getAuthor().getEffectiveName();
        String content = EmojiUtil.unicodeToText(event.getMessage().getContentDisplay());

        Text formatted = buildDiscordMessage(name, content, "✏️ edytował");
        bridge.relayToMinecraft(formatted);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reakcje
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (!config.isRelayDiscordToMinecraft()) return;
        if (event.getUser() != null && event.getUser().isBot()) return;
        if (!isTrackedChannel(event.getChannel().getId())) return;

        String userName  = event.getUser() != null ? event.getUser().getEffectiveName() : "Ktoś";
        String emojiText = emojiToText(event.getEmoji());

        Text formatted = buildDiscordMessage(userName, emojiText, "zareagował");
        bridge.relayToMinecraft(formatted);
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        if (!config.isRelayDiscordToMinecraft()) return;
        if (event.getUser() != null && event.getUser().isBot()) return;
        if (!isTrackedChannel(event.getChannel().getId())) return;

        String userName  = event.getUser() != null ? event.getUser().getEffectiveName() : "Ktoś";
        String emojiText = emojiToText(event.getEmoji());

        Text formatted = buildDiscordMessage(userName, emojiText, "usunął reakcję");
        bridge.relayToMinecraft(formatted);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Buduje sformatowaną wiadomość do wyświetlenia w grze.
     * Format: §9[Discord]§r §b<Nazwa>§r [prefix] §f<treść>
     */
    private Text buildDiscordMessage(String username, String content, String actionPrefix) {
        MutableText prefix = Text.literal(config.getDiscordPrefix() + " ")
                .formatted(Formatting.RESET);

        MutableText namePart = Text.literal(username)
                .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true));

        MutableText middle;
        if (actionPrefix != null && !actionPrefix.isBlank()) {
            middle = Text.literal(" " + actionPrefix + ": ")
                    .formatted(Formatting.GRAY);
        } else {
            middle = Text.literal(": ").formatted(Formatting.GRAY);
        }

        MutableText contentPart = Text.literal(content)
                .formatted(Formatting.WHITE);

        return prefix.append(namePart).append(middle).append(contentPart);
    }

    private boolean isTrackedChannel(String channelId) {
        // Śledź kanał czatu oraz opcjonalne kanały wymiarów
        if (config.getChatChannelId().equals(channelId)) return true;
        return config.getChannelDimensions().containsValue(channelId);
    }

    private static String emojiToText(Emoji emoji) {
        if (emoji instanceof CustomEmoji ce) {
            return ":" + ce.getName() + ":";
        }
        String formatted = emoji.getFormatted();
        // Spróbuj konwersji Unicode
        return EmojiUtil.unicodeToText(formatted);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }
}
