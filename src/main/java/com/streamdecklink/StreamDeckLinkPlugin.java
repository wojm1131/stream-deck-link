package com.streamdecklink;

import com.google.inject.Provides;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Serves a small JSON snapshot of the local player's state on
 * http://127.0.0.1:&lt;port&gt;/state for the companion Stream Deck plugin.
 *
 * Example response while logged in:
 * {
 *   "loggedIn": true,
 *   "active": true,
 *   "playerName": "Zezima",
 *   "world": 302,
 *   "health":    { "current": 87, "max": 99 },
 *   "prayer":    { "current": 43, "max": 70 },
 *   "energy":    { "current": 73, "max": 100 },
 *   "prayerActive": true,
 *   "runEnabled": true,
 *   "special":   { "current": 100, "max": 100, "enabled": false },
 *   "inventory": { "filled": 17, "total": 28 },
 *   "timestamp": 1732377600000
 * }
 */
@Slf4j
@PluginDescriptor(
	name = "Stream Deck Link",
	description = "Exposes player state (active status, health, prayer points, filled inventory slots) on a local HTTP endpoint for an Elgato Stream Deck plugin",
	tags = {"stream deck", "elgato", "http", "state"}
)
public class StreamDeckLinkPlugin extends Plugin
{
	private static final String LOGGED_OUT_JSON =
		"{\"loggedIn\":false,\"active\":false,\"health\":null,\"prayer\":null,\"energy\":null,"
		+ "\"prayerActive\":false,\"runEnabled\":false,\"special\":null,\"inventory\":null}";

	@Inject
	private Client client;

	@Inject
	private StreamDeckLinkConfig config;

	private HttpServer server;

	/** Latest state snapshot, rebuilt every game tick on the client thread. */
	private volatile String latestJson = LOGGED_OUT_JSON;

	@Provides
	StreamDeckLinkConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(StreamDeckLinkConfig.class);
	}

	@Override
	protected void startUp()
	{
		latestJson = LOGGED_OUT_JSON;
		try
		{
			server = HttpServer.create(
				new InetSocketAddress(InetAddress.getLoopbackAddress(), config.httpPort()), 0);
			server.createContext("/state", exchange ->
			{
				byte[] body = latestJson.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream os = exchange.getResponseBody())
				{
					os.write(body);
				}
			});
			server.setExecutor(Executors.newCachedThreadPool(r ->
			{
				Thread t = new Thread(r, "streamdeck-link-http");
				t.setDaemon(true);
				return t;
			}));
			server.start();
			log.info("Stream Deck Link state server listening on 127.0.0.1:{}", config.httpPort());
		}
		catch (IOException e)
		{
			log.warn("Stream Deck Link could not start its HTTP server on port {}", config.httpPort(), e);
		}
	}

	@Override
	protected void shutDown()
	{
		if (server != null)
		{
			server.stop(0);
			server = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		latestJson = buildStateJson();
	}

	private String buildStateJson()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return LOGGED_OUT_JSON;
		}

		Player local = client.getLocalPlayer();

		int hpCurrent = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int hpMax = client.getRealSkillLevel(Skill.HITPOINTS);
		int prayerCurrent = client.getBoostedSkillLevel(Skill.PRAYER);
		int prayerMax = client.getRealSkillLevel(Skill.PRAYER);
		// getEnergy() returns hundredths of a percent (0-10000); the game orb shows whole percent.
		int energyPercent = client.getEnergy() / 100;

		// Any prayer varbit on means a prayer is active (the orb turns cyan in game).
		boolean prayerActive = false;
		for (Prayer prayer : Prayer.values())
		{
			if (client.getVarbitValue(prayer.getVarbit()) == 1)
			{
				prayerActive = true;
				break;
			}
		}
		// Varp 173 is the run toggle option (OPTION_RUN in the cache varp definitions).
		boolean runEnabled = client.getVarpValue(VarPlayerID.OPTION_RUN) == 1;
		// Special attack percent is stored in tenths of a percent (0-1000).
		int specialPercent = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10;
		boolean specialEnabled = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED) == 1;

		int filled = 0;
		int total = 28;
		ItemContainer inventory = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
		if (inventory != null)
		{
			Item[] items = inventory.getItems();
			total = items.length;
			for (Item item : items)
			{
				if (item.getId() != -1 && item.getQuantity() > 0)
				{
					filled++;
				}
			}
		}

		boolean busy = local != null
			&& (local.getAnimation() != -1 || local.getInteracting() != null);
		// Client ticks are ~20ms, so the idle threshold in ticks is seconds * 50.
		int idleTicks = Math.min(client.getKeyboardIdleTicks(), client.getMouseIdleTicks());
		boolean recentlyActive = idleTicks < config.idleSeconds() * 50;
		boolean active = busy || recentlyActive;

		StringBuilder sb = new StringBuilder(192);
		sb.append("{\"loggedIn\":true");
		sb.append(",\"active\":").append(active);
		sb.append(",\"playerName\":");
		if (local != null && local.getName() != null)
		{
			sb.append('"').append(escapeJson(local.getName())).append('"');
		}
		else
		{
			sb.append("null");
		}
		sb.append(",\"world\":").append(client.getWorld());
		sb.append(",\"health\":{\"current\":").append(hpCurrent)
			.append(",\"max\":").append(hpMax).append('}');
		sb.append(",\"prayer\":{\"current\":").append(prayerCurrent)
			.append(",\"max\":").append(prayerMax).append('}');
		sb.append(",\"energy\":{\"current\":").append(energyPercent)
			.append(",\"max\":100}");
		sb.append(",\"prayerActive\":").append(prayerActive);
		sb.append(",\"runEnabled\":").append(runEnabled);
		sb.append(",\"special\":{\"current\":").append(specialPercent)
			.append(",\"max\":100,\"enabled\":").append(specialEnabled).append('}');
		sb.append(",\"inventory\":{\"filled\":").append(filled)
			.append(",\"total\":").append(total).append('}');
		sb.append(",\"timestamp\":").append(System.currentTimeMillis());
		sb.append('}');
		return sb.toString();
	}

	private static String escapeJson(String s)
	{
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			switch (c)
			{
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				default:
					if (c < 0x20)
					{
						out.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						out.append(c);
					}
			}
		}
		return out.toString();
	}
}
