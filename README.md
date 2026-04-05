# SSMP Discord Bridge

Fabric mod integrujący serwer Minecraft 1.21.1 z botem Discord.  
Zawiera dwukierunkowy chat, zdarzenia serwera, zarządzanie graczami i komendy administracyjne.

---

## Funkcje

### Minecraft → Discord
| Zdarzenie | Opis |
|---|---|
| 💬 Czat gracza | Wiadomości z gry trafiają na skonfigurowany kanał Discord |
| ✍️ Komenda `/me` | Akcja emote gracza |
| 📢 Komenda `/say` | Ogłoszenie administratora |
| ➡️ Dołączenie gracza | Embed z awatarem |
| ⬅️ Wyjście gracza | Embed z awatarem |
| 💀 Śmierć gracza | Pełna wiadomość o śmierci z embed |
| ☠️ Śmierć nazwanego stworzenia | Śmierć mob'a z niestandardową nazwą |
| ✅ Osiągnięcia (task/goal/challenge) | Embed z kolorem zależnym od typu |
| 🌀 Zmiana wymiaru | Informacja o teleportacji |
| 🚀 Uruchomienie serwera | Etap "uruchamia się…" |
| ✅ Serwer gotowy | Zaproszenie do gry |
| 🛑 Zatrzymywanie serwera | Ostrzeżenie |
| ⛔ Serwer offline | Ostatnia wiadomość przed zamknięciem |

### Discord → Minecraft
| Zdarzenie | Opis |
|---|---|
| 💬 Nowa wiadomość | Wyświetlana w czacie z prefixem `[Discord]` |
| ↩️ Odpowiedź | Pokazuje cytowany fragment i autora |
| ✏️ Edycja wiadomości | Informacja o edycji |
| 👍 Dodanie reakcji | Informacja w czacie |
| ➖ Usunięcie reakcji | Informacja w czacie |
| 🖼️ Naklejki | Nazwa naklejki w czacie |
| 📎 Załączniki | Nazwa pliku w czacie |

### Komendy Discord (slash commands)
| Komenda | Opis | Wymagane uprawnienia |
|---|---|---|
| `/status` | Informacje o serwerze (gracze, TPS) | Wszyscy |
| `/lista` | Lista graczy online z wymiarami | Wszyscy |
| `/ping` | Opóźnienie bota | Wszyscy |
| `/whitelist dodaj <gracz>` | Dodaj do białej listy | Admin |
| `/whitelist usuń <gracz>` | Usuń z białej listy | Admin |
| `/whitelist lista` | Wyświetl białą listę | Admin |
| `/ban <gracz> [powód]` | Zbanuj gracza | Admin |
| `/unban <gracz>` | Odbanuj gracza | Admin |
| `/op <gracz>` | Nadaj OP | Admin |
| `/deop <gracz>` | Cofnij OP | Admin |
| `/wykonaj <komenda>` | Wykonaj dowolną komendę | Admin |

---

## Wymagania

- Minecraft **1.21.1** (serwer dedykowany)
- [Fabric Loader](https://fabricmc.net/use/installer/) ≥ 0.15.0
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.102.0+1.21.1
- Java **21**

---

## Konfiguracja

### Metoda 1: Plik konfiguracyjny

Przy pierwszym uruchomieniu serwera mod automatycznie tworzy plik:
```
config/ssmp_mod.json
```

Przykładowa zawartość:
```json
{
  "token": "BOT_TOKEN",
  "guild_id": "GUILD_ID",
  "chat_channel_id": "CHANNEL_ID",
  "events_channel_id": "CHANNEL_ID",
  "console_channel_id": "",
  "admin_user_ids": ["USER_ID_1", "USER_ID_2"],
  "channel_dimensions": {
    "minecraft:overworld": "CHANNEL_ID_OVERWORLD",
    "minecraft:the_nether": "CHANNEL_ID_NETHER",
    "minecraft:the_end": "CHANNEL_ID_END"
  },
  "server_name": "SSMP",
  "show_avatars": true,
  "relay_discord_to_minecraft": true,
  "show_discord_prefix": true,
  "discord_prefix": "§9[Discord]§r"
}
```

### Metoda 2: Zmienne środowiskowe (Docker)

```env
DISCORD_TOKEN=bot_token_tutaj
DISCORD_GUILD_ID=id_serwera_discord
DISCORD_CHAT_CHANNEL_ID=id_kanalu_czatu
DISCORD_EVENTS_CHANNEL_ID=id_kanalu_zdarzen
DISCORD_CONSOLE_CHANNEL_ID=id_kanalu_konsoli
DISCORD_ADMIN_USER_IDS=id_admina_1,id_admina_2
SERVER_NAME=SSMP
```

Zmienne środowiskowe **nadpisują** wartości z pliku JSON.

### Docker Compose — przykład

```yaml
services:
  minecraft:
    image: itzg/minecraft-server
    environment:
      EULA: "TRUE"
      TYPE: FABRIC
      VERSION: "1.21.1"
      FABRIC_LOADER_VERSION: "0.16.5"
      # Discord Bridge
      DISCORD_TOKEN: "${DISCORD_TOKEN}"
      DISCORD_GUILD_ID: "${DISCORD_GUILD_ID}"
      DISCORD_CHAT_CHANNEL_ID: "${DISCORD_CHAT_CHANNEL_ID}"
      DISCORD_EVENTS_CHANNEL_ID: "${DISCORD_EVENTS_CHANNEL_ID}"
      DISCORD_ADMIN_USER_IDS: "${DISCORD_ADMIN_USER_IDS}"
      SERVER_NAME: "SSMP"
    volumes:
      - ./data:/data
    ports:
      - "25565:25565"
```

---

## Tworzenie bota Discord

1. Przejdź do [Discord Developer Portal](https://discord.com/developers/applications)
2. Utwórz nową aplikację → Bot → **Reset Token** → skopiuj token
3. W zakładce **Bot**:
   - Włącz `MESSAGE CONTENT INTENT`
   - Włącz `SERVER MEMBERS INTENT` (opcjonalnie)
4. **Zaproś bota** na serwer Discord z uprawnieniami:
   - `Send Messages`
   - `Embed Links`
   - `Read Message History`
   - `Add Reactions`
   - `Use Slash Commands`

OAuth2 URL:
```
https://discord.com/api/oauth2/authorize?client_id=CLIENT_ID&permissions=274878000128&scope=bot%20applications.commands
```

---

## Multi-channel (wymiary → kanały)

Każdy wymiar może mieć dedykowany kanał Discord dla czatu graczy:

```json
"channel_dimensions": {
  "minecraft:overworld": "123456789",
  "minecraft:the_nether": "987654321",
  "minecraft:the_end": "111222333"
}
```

Wiadomości czatu wysyłane są na kanał odpowiadający wymiarowi, w którym przebywa gracz.  
Jeśli wymiar nie ma przypisanego kanału, używany jest domyślny `chat_channel_id`.

---

## Budowanie ze źródeł

```bash
git clone https://github.com/ManFcG/ssmp_mod_1.21.1.git
cd ssmp_mod_1.21.1
./gradlew build
```

Zbudowany mod znajdziesz w `build/libs/ssmp_mod-<wersja>.jar`.

---

## Uprawnienia administratora Discord

Komendy administracyjne (`/ban`, `/unban`, `/op`, `/deop`, `/whitelist dodaj/usuń`, `/wykonaj`) są dostępne wyłącznie dla użytkowników Discord, których ID są wpisane w `admin_user_ids`.

Jak znaleźć swoje ID:
1. Ustawienia Discord → Zaawansowane → **Tryb dewelopera**: włącz
2. Kliknij prawym przyciskiem na swój awatar → **Kopiuj ID użytkownika**

---

## Licencja

MIT