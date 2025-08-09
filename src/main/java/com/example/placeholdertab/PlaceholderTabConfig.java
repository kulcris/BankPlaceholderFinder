package com.example.placeholdertab;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("placeholdertab")
public interface PlaceholderTabConfig extends Config
{
    @ConfigItem(
        keyName = "tabName",
        name = "Tab label",
        description = "Text shown on the Placeholder bank tab"
    )
    default String tabName()
    {
        return "Placeholders";
    }

    @ConfigItem(
        keyName = "toggleHotkey",
        name = "Toggle hotkey",
        description = "Press to toggle showing only placeholder items in your bank"
    )
    default Keybind toggleHotkey()
    {
        return Keybind.NOT_SET; // set this in config
    }
}
