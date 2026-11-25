package com.streamdecklink;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("streamdecklink")
public interface StreamDeckLinkConfig extends Config
{
	@Range(
		min = 1024,
		max = 65535
	)
	@ConfigItem(
		keyName = "httpPort",
		name = "HTTP port",
		description = "Local port the state server listens on. Must match the port set in the Stream Deck plugin's settings.",
		position = 1
	)
	default int httpPort()
	{
		return 8765;
	}

	@Range(
		min = 5,
		max = 600
	)
	@ConfigItem(
		keyName = "idleSeconds",
		name = "Idle threshold (seconds)",
		description = "The player counts as active if there was keyboard/mouse input, or the character was animating or interacting, within this many seconds.",
		position = 2
	)
	default int idleSeconds()
	{
		return 60;
	}
}
