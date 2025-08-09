package com.example;

import com.example.placeholdertab.PlaceholderTabPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PlaceholderTabPlugin.class);
		RuneLite.main(args);
	}
}