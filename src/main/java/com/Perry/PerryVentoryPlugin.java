package com.Perry;

// --- Keep existing imports ---
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.config.ConfigManager; // Import ConfigManager
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.game.ItemManager;
// Removed panel/toolbar imports

// Import Gson and TypeToken
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap; // Keep if preferred, or switch back

@Slf4j
@PluginDescriptor(
		name = "PerryVentory",
		description = "Keeps relocating shadows of items that leave the inventory, allowing duplicates but removing one on pickup. Shadows persist across sessions.", // Updated desc
		tags = {"inventory", "items", "shadow", "equip", "consume", "drop", "persistent", "move", "duplicate", "perry"}
)
public class PerryVentoryPlugin extends Plugin {

	// Config group name - must match @ConfigGroup in PerryVentoryConfig
	private static final String CONFIG_GROUP = "perryventory";
	// Key to store shadow data under
	private static final String SHADOW_DATA_KEY = "reservedSlotsData";

	// --- Injections ---
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private EventBus eventBus;
	@Inject private OverlayManager overlayManager;
	@Inject private ReservedSlotOverlay overlay;
	@Inject private PerryVentoryConfig config;
	@Inject private ConfigManager configManager; // Inject ConfigManager
	@Inject private ItemManager itemManager;
	// Removed ChatCommandManager, ClientToolbar

	// --- Fields ---
	@Getter
	// Using ConcurrentHashMap might still be slightly safer if saving happens async, but HashMap is likely fine if load/save/logic are all on client thread
	private final Map<Integer, Integer> reservedSlots = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> previousInventoryState = new HashMap<>();
	private static final int COINS = ItemID.COINS_995;

	// Gson instance for JSON serialization
	@Inject // Let Guice provide Gson instance if available, otherwise create new Gson()
	private Gson gson;

	// Type token for deserializing Map<Integer, Integer>
	private static final Type SHADOW_MAP_TYPE = new TypeToken<Map<Integer, Integer>>() {}.getType();

	// Removed Panel/NavButton fields

	// --- Config Provider ---
	@Provides
	PerryVentoryConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(PerryVentoryConfig.class);
	}

	// --- Lifecycle Methods ---
	@Override
	protected void startUp() throws Exception {
		// Load saved shadows *before* potentially processing any initial inventory state
		// Ensure Gson is available
		if (gson == null) { gson = new Gson(); } // Basic fallback
		clientThread.invokeLater(() -> {
			loadShadows();
			// Initialize previous state *after* loading potential shadows
			// to prevent initial state thinking items appeared when shadows were loaded
			initializeInventoryState();
			// Now register event listeners
			eventBus.register(this);
			overlayManager.add(overlay);
			log.info("PerryVentory started!");
		});
	}

	@Override
	protected void shutDown() throws Exception {
		eventBus.unregister(this); // Unregister first to stop processing events
		overlayManager.remove(overlay);
		// Save final state on shutdown
		saveShadows();
		reservedSlots.clear();
		previousInventoryState.clear();
		log.info("PerryVentory stopped!");
	}

	// --- State Helpers ---
	private void initializeInventoryState() {
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory != null) { updatePreviousInventoryState(inventory.getItems()); log.debug("Initialized previous inventory state."); }
		else { previousInventoryState.clear(); log.warn("Could not initialize inventory state - container not found."); }
	}
	private void updatePreviousInventoryState(Item[] items) {
		previousInventoryState.clear();
		if (items == null) return;
		for (int slot = 0; slot < items.length; slot++) { if (items[slot] != null && items[slot].getId() != -1) { previousInventoryState.put(slot, items[slot].getId()); } }
	}

	// --- Actions ---
	public void clearAllShadows() {
		clientThread.invokeLater(() -> {
			if (!reservedSlots.isEmpty()) {
				reservedSlots.clear();
				log.info("Cleared all item shadows via panel button.");
				// Save the cleared state
				saveShadows();
				final String notification = new ChatMessageBuilder().append(ChatColorType.NORMAL).append("All PerryVentory shadows cleared.").build();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", notification, "");
				// Panel update removed as panel was removed
			} else {
				final String notification = new ChatMessageBuilder().append(ChatColorType.NORMAL).append("No PerryVentory shadows to clear.").build();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", notification, "");
			}
		});
	}

	// Removed removeShadow method (was for panel)

	// Removed getReservedSlotsView method (was for panel)


	// --- Persistence Methods ---

	private void loadShadows() {
		// Must run on client thread if it modifies previousInventoryState or reservedSlots accessed by events
		String json = configManager.getConfiguration(CONFIG_GROUP, SHADOW_DATA_KEY);
		reservedSlots.clear(); // Start fresh

		if (json != null && !json.isEmpty()) {
			try {
				Map<Integer, Integer> loadedMap = gson.fromJson(json, SHADOW_MAP_TYPE);
				if (loadedMap != null) {
					reservedSlots.putAll(loadedMap); // Load the saved shadows
					log.debug("Loaded {} shadows from config.", reservedSlots.size());
				}
			} catch (Exception e) {
				log.error("Error loading PerryVentory shadow data from config", e);
				// Optionally clear potentially corrupted data
				// configManager.unsetConfiguration(CONFIG_GROUP, SHADOW_DATA_KEY);
			}
		} else {
			log.debug("No previous shadow data found to load.");
		}
	}

	private void saveShadows() {
		// Should run on client thread if reservedSlots isn't fully thread-safe for serialization
		if (reservedSlots.isEmpty()) {
			// Clear the setting if map is empty
			configManager.unsetConfiguration(CONFIG_GROUP, SHADOW_DATA_KEY);
			log.debug("Cleared shadow data in config as map is empty.");
		} else {
			try {
				String json = gson.toJson(reservedSlots);
				configManager.setConfiguration(CONFIG_GROUP, SHADOW_DATA_KEY, json);
				log.debug("Saved {} shadows to config.", reservedSlots.size());
			} catch (Exception e) {
				log.error("Error saving PerryVentory shadow data to config", e);
			}
		}
	}


	// --- Main Event Logic ---
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
			// Run processing on client thread
			clientThread.invokeLater(() -> processInventoryChange(event));
		}
	}

	private void processInventoryChange(ItemContainerChanged event) {
		boolean changed = false; // Flag to track if shadows were modified
		ItemContainer currentContainer = event.getItemContainer();
		// Null checks...
		if (currentContainer == null) return;
		Item[] currentItems = currentContainer.getItems();
		if (currentItems == null) return;
		int inventorySize = currentContainer.size();

		// --- Phase 1: Analyze changes and identify moves ---
		Map<Integer, Integer> removedItems = new HashMap<>();
		Map<Integer, Integer> addedItems = new HashMap<>();
		Set<Integer> movedItemIds = new HashSet<>();
		// ... (Populate maps/set logic) ...
		for (int slot = 0; slot < inventorySize; slot++) {
			int previousItemId = previousInventoryState.getOrDefault(slot, -1);
			int currentItemId = (slot < currentItems.length && currentItems[slot] != null) ? currentItems[slot].getId() : -1;
			if (previousItemId != -1 && currentItemId == -1) { removedItems.put(slot, previousItemId); }
			else if (previousItemId == -1 && currentItemId != -1) { addedItems.put(slot, currentItemId); }
			else if (previousItemId != -1 && currentItemId != -1 && previousItemId != currentItemId) { removedItems.put(slot, previousItemId); addedItems.put(slot, currentItemId); }
		}
		for (Map.Entry<Integer, Integer> removedEntry : removedItems.entrySet()) { if (addedItems.containsValue(removedEntry.getValue())) { movedItemIds.add(removedEntry.getValue()); } }
		// --- End Phase 1 ---

		// --- Phase 2: Process added items ---
		for (Map.Entry<Integer, Integer> addedEntry : addedItems.entrySet()) { /* ... */
			int addedItemId = addedEntry.getValue();
			if (!movedItemIds.contains(addedItemId)) {
				int idToCheck = addedItemId;
				try { /* ... normalize coins ... */
					ItemComposition definition = client.getItemDefinition(idToCheck);
					if (definition != null && definition.getName().equalsIgnoreCase("Coins")) { idToCheck = COINS; }
				} catch (Exception e) { log.error("Error looking up item definition for ID {}: {}", idToCheck, e.getMessage()); }
				int shadowSlotToRemove = -1;
				for (Map.Entry<Integer, Integer> shadowEntry : reservedSlots.entrySet()) {
					if (shadowEntry.getValue() == idToCheck) { shadowSlotToRemove = shadowEntry.getKey(); break; }
				}
				if (shadowSlotToRemove != -1) {
					if (reservedSlots.remove(shadowSlotToRemove) != null) { // Check remove was successful
						log.debug("Removed shadow for item ID {} from slot {} because the real item was added externally.", idToCheck, shadowSlotToRemove);
						changed = true; // Mark state changed
					}
				}
			} // else { log internal move }
		}
		// --- End Phase 2 ---

		// --- Phase 3: Process removed items ---
		for (Map.Entry<Integer, Integer> removedEntry : removedItems.entrySet()) { /* ... */
			int removedItemId = removedEntry.getValue();
			int removedSlot = removedEntry.getKey();
			if (!movedItemIds.contains(removedItemId)) {
				int shadowItemId = removedItemId;
				try { /* ... normalize coins ... */
					ItemComposition definition = client.getItemDefinition(shadowItemId);
					if (definition != null && definition.getName().equalsIgnoreCase("Coins")) { shadowItemId = COINS; }
				} catch (Exception e) { log.error("Error looking up item definition for ID {}: {}", shadowItemId, e.getMessage()); }
				reservedSlots.put(removedSlot, shadowItemId);
				log.debug("Item {} left slot {} externally. Added shadow with ID {} to slot {}.", removedItemId, removedSlot, shadowItemId, removedSlot);
				changed = true; // Mark state changed
			} // else { log internal move }
		}
		// --- End Phase 3 ---

		// --- Phase 4: Handle Displaced Shadows ---
		List<Integer> shadowsToReassign = new ArrayList<>();
		Iterator<Map.Entry<Integer, Integer>> iterator = reservedSlots.entrySet().iterator();
		while (iterator.hasNext()) { /* ... */
			Map.Entry<Integer, Integer> entry = iterator.next();
			int slot = entry.getKey();
			int shadowItemId = entry.getValue();
			boolean slotOccupied = (slot >= 0 && slot < currentItems.length && currentItems[slot] != null && currentItems[slot].getId() != -1);
			if (slotOccupied) {
				log.debug("Slot {} is now occupied, displacing shadow item {}", slot, shadowItemId);
				iterator.remove(); // Safe removal via iterator
				shadowsToReassign.add(shadowItemId);
				log.debug("Marking shadow item ID {} for potential relocation.", shadowItemId);
				changed = true; // Mark state changed (removal part)
			}
		}
		// Try relocation loop...
		if (!shadowsToReassign.isEmpty()) {
			log.debug("Trying to relocate {} displaced shadows.", shadowsToReassign.size());
			Item[] latestItems = currentContainer.getItems();
			for (int itemIdToPlace : shadowsToReassign) {
				boolean placed = tryReassignShadow(itemIdToPlace, latestItems, reservedSlots, inventorySize);
				if (placed) {
					changed = true; // Mark state changed (successful add part)
				} else {
					log.warn("Could not find a free slot to relocate shadow for item ID {}. Shadow lost.", itemIdToPlace);
				}
			}
		}
		// --- End Phase 4 ---

		// --- Phase 5: Update the previous state map ---
		updatePreviousInventoryState(currentItems);

		// --- Save if changes occurred ---
		if (changed) {
			saveShadows();
		}
	}


	// --- tryReassignShadow helper function ---
	private boolean tryReassignShadow(int shadowItemId, Item[] currentItems, Map<Integer, Integer> currentReservedSlots, int inventorySize) {
		for (int slot = 0; slot < inventorySize; slot++) {
			boolean slotIsEmpty = !(slot >= 0 && slot < currentItems.length && currentItems[slot] != null && currentItems[slot].getId() != -1);
			boolean slotIsAvailableForShadow = !currentReservedSlots.containsKey(slot);

			if (slotIsEmpty && slotIsAvailableForShadow) {
				currentReservedSlots.put(slot, shadowItemId);
				log.debug("Relocated shadow for item ID {} to available slot {}", shadowItemId, slot);
				return true; // A shadow was placed (state changed)
			}
		}
		return false; // No shadow was placed
	}

} // End of class PerryVentoryPlugin