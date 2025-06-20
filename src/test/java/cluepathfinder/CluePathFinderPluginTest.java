package cluepathfinder;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CluePathFinderPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CluePathFinderPlugin.class);
		RuneLite.main(args);
	}
}