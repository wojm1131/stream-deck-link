package com.streamdecklink;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class StreamDeckLinkPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(StreamDeckLinkPlugin.class);
		RuneLite.main(args);
	}
}
