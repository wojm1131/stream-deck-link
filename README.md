# Stream Deck Link

A RuneLite plugin that exposes local player state on a small HTTP endpoint so
the companion **RuneLite Stats** Elgato Stream Deck plugin can display it on
Stream Deck keys: player status (active / idle / logged out), health, prayer
points, run energy, special attack energy, and filled inventory slots.

## What it does

Every game tick the plugin rebuilds a JSON snapshot of the local player's
state and serves it at `http://127.0.0.1:8765/state`:

```json
{
  "loggedIn": true,
  "active": true,
  "playerName": "Zezima",
  "world": 302,
  "health":    { "current": 87, "max": 99 },
  "prayer":    { "current": 43, "max": 70 },
  "energy":    { "current": 73, "max": 100 },
  "prayerActive": true,
  "runEnabled": true,
  "special":   { "current": 100, "max": 100, "enabled": false },
  "inventory": { "filled": 17, "total": 28 },
  "timestamp": 1732377600000
}
```

## Safety

- The server binds to **127.0.0.1 only** — nothing is exposed to your network.
- It is **read-only**: it never sends input to the game and never modifies
  game state. It only reads values the client already displays (the data
  orbs, inventory), via the RuneLite API.
- "Active" means: logged in, and either your character is animating or
  interacting, or there was keyboard/mouse input within the idle threshold.

## Configuration

- **HTTP port** (default 8765)
- **Idle threshold** in seconds (default 60) for the active/idle status

## Companion app

This plugin does nothing visible on its own. Install the **RuneLite Stats**
Stream Deck plugin and drag its keys (Player Status, Health, Prayer, Run
Energy, Special Attack, Inventory) onto your Stream Deck; they poll this
plugin's endpoint every 750 ms and render the values with the game's own orb
iconography, including state colours (prayer orb turns cyan while prayers are
active, run orb turns gold while run is toggled on, special attack orb turns
light blue while a special attack is queued).
