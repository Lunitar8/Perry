package com.Perry;

import net.runelite.client.config.*;

import java.awt.*;
// Removed Button import

@ConfigGroup(PerryVentoryPlugin.CONFIG_GROUP)
public interface PerryVentoryConfig extends Config {

	// --- Config for Appearance ---
	@Alpha
	@ConfigItem(
			keyName = "shadowColor",
			name = "Shadow Color & Opacity",
			description = "Sets the color tint and transparency for ALL shadows (automatic and manual).",
			position = 1
	)
	default Color shadowColor() {
		return new Color(0, 0, 0, 128); // Default semi-transparent black
	}

	// --- Input Field to Clear Specific Slot ---
	@ConfigItem(
			keyName = "clearSlotNumberInput",
			name = "Clear Specific Slot (Enter 0-27)",
			description = "Enter slot number (0-27) and press Enter/click away to clear its shadow. Field resets after use.",
			position = 6 // Position near top
	)
	default String clearSlotNumberInput() {
		return ""; // Default to empty string
	}


	// --- Section for Manual Shadow Configuration (Item IDs) ---
	@ConfigSection(
			name = "Manual Shadow Configuration (Item IDs)",
			description = "Manually specify ITEM IDs for shadows. These exist separately from automatic shadows.",
			position = 10,
			closedByDefault = true
	)
	String manualSlotsSection = "manualSlotsSection";

	// --- Config Items for Manual Slots (Expecting Item IDs) ---
	// Slots 1-28 (Key names slot0Item to slot27Item)

	@ConfigItem(keyName = "slot0Item", name = "Slot 1 Manual Item ID", description = "Manually set a shadow item ID for slot 1. Enter the numeric Item ID.", position = 11, section = manualSlotsSection)
	default String slot0Item() { return ""; }

	@ConfigItem(keyName = "slot1Item", name = "Slot 2 Manual Item ID", description = "Manually set a shadow item ID for slot 2. Enter the numeric Item ID.", position = 12, section = manualSlotsSection)
	default String slot1Item() { return ""; }

	@ConfigItem(keyName = "slot2Item", name = "Slot 3 Manual Item ID", description = "Manually set a shadow item ID for slot 3. Enter the numeric Item ID.", position = 13, section = manualSlotsSection)
	default String slot2Item() { return ""; }

	@ConfigItem(keyName = "slot3Item", name = "Slot 4 Manual Item ID", description = "Manually set a shadow item ID for slot 4. Enter the numeric Item ID.", position = 14, section = manualSlotsSection)
	default String slot3Item() { return ""; }

	@ConfigItem(keyName = "slot4Item", name = "Slot 5 Manual Item ID", description = "Manually set a shadow item ID for slot 5. Enter the numeric Item ID.", position = 15, section = manualSlotsSection)
	default String slot4Item() { return ""; }

	@ConfigItem(keyName = "slot5Item", name = "Slot 6 Manual Item ID", description = "Manually set a shadow item ID for slot 6. Enter the numeric Item ID.", position = 16, section = manualSlotsSection)
	default String slot5Item() { return ""; }

	@ConfigItem(keyName = "slot6Item", name = "Slot 7 Manual Item ID", description = "Manually set a shadow item ID for slot 7. Enter the numeric Item ID.", position = 17, section = manualSlotsSection)
	default String slot6Item() { return ""; }

	@ConfigItem(keyName = "slot7Item", name = "Slot 8 Manual Item ID", description = "Manually set a shadow item ID for slot 8. Enter the numeric Item ID.", position = 18, section = manualSlotsSection)
	default String slot7Item() { return ""; }

	@ConfigItem(keyName = "slot8Item", name = "Slot 9 Manual Item ID", description = "Manually set a shadow item ID for slot 9. Enter the numeric Item ID.", position = 19, section = manualSlotsSection)
	default String slot8Item() { return ""; }

	@ConfigItem(keyName = "slot9Item", name = "Slot 10 Manual Item ID", description = "Manually set a shadow item ID for slot 10. Enter the numeric Item ID.", position = 20, section = manualSlotsSection)
	default String slot9Item() { return ""; }

	@ConfigItem(keyName = "slot10Item", name = "Slot 11 Manual Item ID", description = "Manually set a shadow item ID for slot 11. Enter the numeric Item ID.", position = 21, section = manualSlotsSection)
	default String slot10Item() { return ""; }

	@ConfigItem(keyName = "slot11Item", name = "Slot 12 Manual Item ID", description = "Manually set a shadow item ID for slot 12. Enter the numeric Item ID.", position = 22, section = manualSlotsSection)
	default String slot11Item() { return ""; }

	@ConfigItem(keyName = "slot12Item", name = "Slot 13 Manual Item ID", description = "Manually set a shadow item ID for slot 13. Enter the numeric Item ID.", position = 23, section = manualSlotsSection)
	default String slot12Item() { return ""; }

	@ConfigItem(keyName = "slot13Item", name = "Slot 14 Manual Item ID", description = "Manually set a shadow item ID for slot 14. Enter the numeric Item ID.", position = 24, section = manualSlotsSection)
	default String slot13Item() { return ""; }

	@ConfigItem(keyName = "slot14Item", name = "Slot 15 Manual Item ID", description = "Manually set a shadow item ID for slot 15. Enter the numeric Item ID.", position = 25, section = manualSlotsSection)
	default String slot14Item() { return ""; }

	@ConfigItem(keyName = "slot15Item", name = "Slot 16 Manual Item ID", description = "Manually set a shadow item ID for slot 16. Enter the numeric Item ID.", position = 26, section = manualSlotsSection)
	default String slot15Item() { return ""; }

	@ConfigItem(keyName = "slot16Item", name = "Slot 17 Manual Item ID", description = "Manually set a shadow item ID for slot 17. Enter the numeric Item ID.", position = 27, section = manualSlotsSection)
	default String slot16Item() { return ""; }

	@ConfigItem(keyName = "slot17Item", name = "Slot 18 Manual Item ID", description = "Manually set a shadow item ID for slot 18. Enter the numeric Item ID.", position = 28, section = manualSlotsSection)
	default String slot17Item() { return ""; }

	@ConfigItem(keyName = "slot18Item", name = "Slot 19 Manual Item ID", description = "Manually set a shadow item ID for slot 19. Enter the numeric Item ID.", position = 29, section = manualSlotsSection)
	default String slot18Item() { return ""; }

	@ConfigItem(keyName = "slot19Item", name = "Slot 20 Manual Item ID", description = "Manually set a shadow item ID for slot 20. Enter the numeric Item ID.", position = 30, section = manualSlotsSection)
	default String slot19Item() { return ""; }

	@ConfigItem(keyName = "slot20Item", name = "Slot 21 Manual Item ID", description = "Manually set a shadow item ID for slot 21. Enter the numeric Item ID.", position = 31, section = manualSlotsSection)
	default String slot20Item() { return ""; }

	@ConfigItem(keyName = "slot21Item", name = "Slot 22 Manual Item ID", description = "Manually set a shadow item ID for slot 22. Enter the numeric Item ID.", position = 32, section = manualSlotsSection)
	default String slot21Item() { return ""; }

	@ConfigItem(keyName = "slot22Item", name = "Slot 23 Manual Item ID", description = "Manually set a shadow item ID for slot 23. Enter the numeric Item ID.", position = 33, section = manualSlotsSection)
	default String slot22Item() { return ""; }

	@ConfigItem(keyName = "slot23Item", name = "Slot 24 Manual Item ID", description = "Manually set a shadow item ID for slot 24. Enter the numeric Item ID.", position = 34, section = manualSlotsSection)
	default String slot23Item() { return ""; }

	@ConfigItem(keyName = "slot24Item", name = "Slot 25 Manual Item ID", description = "Manually set a shadow item ID for slot 25. Enter the numeric Item ID.", position = 35, section = manualSlotsSection)
	default String slot24Item() { return ""; }

	@ConfigItem(keyName = "slot25Item", name = "Slot 26 Manual Item ID", description = "Manually set a shadow item ID for slot 26. Enter the numeric Item ID.", position = 36, section = manualSlotsSection)
	default String slot25Item() { return ""; }

	@ConfigItem(keyName = "slot26Item", name = "Slot 27 Manual Item ID", description = "Manually set a shadow item ID for slot 27. Enter the numeric Item ID.", position = 37, section = manualSlotsSection)
	default String slot26Item() { return ""; }

	@ConfigItem(keyName = "slot27Item", name = "Slot 28 Manual Item ID", description = "Manually set a shadow item ID for slot 28. Enter the numeric Item ID.", position = 38, section = manualSlotsSection)
	default String slot27Item() { return ""; }

	/* --- Optional toggles --- */
     /*
    @ConfigItem(keyName = "relocationEnabled", name = "Enable Automatic Shadow Relocation", description = "If enabled, AUTOMATIC shadows try to move when overwritten.", position = 50 )
    default boolean relocationEnabled() { return true; }
    @ConfigItem( keyName = "duplicatesEnabled", name = "Allow Duplicate Automatic Shadows", description = "If enabled, dropping multiple identical items creates multiple AUTOMATIC shadows.", position = 51 )
    default boolean duplicatesEnabled() { return true; }
    */
}