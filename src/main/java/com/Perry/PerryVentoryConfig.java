package com.Perry;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("perryventory") // Unique group name for your plugin's settings
public interface PerryVentoryConfig extends Config {

	@Alpha // Allows the user to select transparency along with the color
	@ConfigItem(
			keyName = "shadowColor",
			name = "Shadow Color & Opacity",
			description = "Sets the color tint and transparency of the shadows. Adjust the Alpha slider for opacity.",
			position = 1 // Order in the config panel
	)
	default Color shadowColor() {
		// Default to semi-transparent black (50% opacity)
		return new Color(0, 0, 0, 128);
	}

    /* Optional future toggles - uncomment and implement logic in PerryVentoryPlugin if desired
    @ConfigItem(
            keyName = "relocationEnabled",
            name = "Enable Shadow Relocation",
            description = "If enabled, shadows will try to move to an empty slot when overwritten. If disabled, they disappear.",
            position = 10
    )
    default boolean relocationEnabled() { return true; }

    @ConfigItem(
            keyName = "duplicatesEnabled", // Note: Current logic effectively hardcodes this to true
            name = "Allow Duplicate Shadows",
            description = "If enabled, dropping multiple identical items creates multiple shadows.",
            position = 11
    )
    default boolean duplicatesEnabled() { return true; }
    */
}