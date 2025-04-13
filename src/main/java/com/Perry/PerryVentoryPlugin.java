package com.Perry;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


@Slf4j
@PluginDescriptor(
		name = "PerryVentory",
		description = "Automatic shadows + Manual shadows via Item ID config. Clear specific slot via config.",
		tags = {"inventory", "items", "shadow", "persistent", "move", "manual", "config", "id", "clear", "perry"}
)
public class PerryVentoryPlugin extends Plugin {

	// --- Constants ---
	public static final String CONFIG_GROUP = "perryventory";
	private static final String SHADOW_DATA_KEY = "reservedSlotsData";
	private static final int COINS = ItemID.COINS_995;
	private static final Type SHADOW_MAP_TYPE = new TypeToken<Map<Integer, Integer>>() {}.getType();
	private static final String CLEAR_SHADOW_OPTION = "Clear PerryVentory Shadow";
	private static final String TARGET_OPTION = "Cancel";
	// Removed CLEAR_CMD constant

	// --- Injections ---
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private EventBus eventBus;
	@Inject private OverlayManager overlayManager;
	@Inject private ReservedSlotOverlay overlay;
	@Inject private PerryVentoryConfig config;
	@Inject private ConfigManager configManager;
	@Inject private ItemManager itemManager;
	@Inject private Gson gson;
	// Removed ChatCommandManager injection

	// --- Fields ---
	@Getter
	private final Map<Integer, Integer> reservedSlots = new ConcurrentHashMap<>();
	@Getter
	private final Map<Integer, Integer> manualShadows = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> previousInventoryState = new HashMap<>();
	private volatile boolean needsToLoadConfig = false;


	// --- Config Provider ---
	@Provides
	PerryVentoryConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(PerryVentoryConfig.class);
	}

	// --- Lifecycle Methods ---
	@Override
	protected void startUp() throws Exception {
		log.info("Starting PerryVentory Plugin...");
		if (gson == null) { gson = new Gson(); }
		previousInventoryState.clear();
		reservedSlots.clear();
		manualShadows.clear();
		needsToLoadConfig = false;

		clientThread.invokeLater(() -> {
			updateManualShadowsFromConfig(); // Load initial manual config state
			// Clear the input field on startup just in case it had a value saved
			try {
				configManager.setConfiguration(CONFIG_GROUP, "clearSlotNumberInput", "");
			} catch (Exception e) { log.warn("Could not clear slot input field on startup", e);}

			// Load automatic shadows is triggered later
			eventBus.register(this);
			overlayManager.add(overlay);
			log.info("PerryVentory started! Waiting for login and inventory widget load.");
		});
	}

	@Override
	protected void shutDown() throws Exception {
		log.info("Stopping PerryVentory Plugin...");
		eventBus.unregister(this);
		overlayManager.remove(overlay);
		// Removed command unregistration
		if (!previousInventoryState.isEmpty() || !reservedSlots.isEmpty()) {
			log.info("Shutting down plugin, saving final automatic shadow state...");
			saveShadows(); // Save automatic shadows
		}
		reservedSlots.clear();
		manualShadows.clear();
		previousInventoryState.clear();
		needsToLoadConfig = false;
		log.info("PerryVentory stopped!");
	}


	// --- Event Subscribers ---

	/**
	 * Clears previous inventory state map and resets load flag upon logout/hop.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		GameState newState = gameStateChanged.getGameState();
		log.debug("GameState changed to: {}", newState);

		if (newState == GameState.LOGGED_IN) {
			log.trace("LOGGED_IN state detected.");
		}
		// Clear previous state map and reset flags on logout/hop
		else if (previousInventoryState != null && !previousInventoryState.isEmpty() && (
				newState == GameState.LOGIN_SCREEN || newState == GameState.CONNECTION_LOST || newState == GameState.HOPPING)) {
			log.debug("Player logged out or hopping ({}) Clearing previous state map.", newState);
			previousInventoryState.clear();
			if (needsToLoadConfig) { // Reset flag if logout happens before load trigger
				log.debug("Resetting needsToLoadConfig flag due to game state change.");
				needsToLoadConfig = false;
			}
		}
	}

	/**
	 * Initializes previous inventory state and sets flag to load saved shadows.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
		if (widgetLoaded.getGroupId() == WidgetInfo.INVENTORY.getGroupId()) {
			log.debug("Inventory widget group loaded (ID: {}).", widgetLoaded.getGroupId());
			if (client.getGameState() == GameState.LOGGED_IN) {
				if (previousInventoryState.isEmpty()) {
					log.debug("Inventory loaded after login, previous state empty. Queueing state initialization.");
					clientThread.invokeLater(this::initializeInventoryState);
				}
				if (reservedSlots.isEmpty() && !needsToLoadConfig) {
					needsToLoadConfig = true;
					log.debug("Inventory loaded after login, reservedSlots empty. Flag set to load config on next tick.");
				}
			}
		}
	}

	/**
	 * Loads saved automatic shadow data on the first game tick after the load flag is set.
	 */
	@Subscribe
	public void onGameTick(GameTick gameTick) {
		if (needsToLoadConfig) {
			needsToLoadConfig = false;
			log.debug("First GameTick after inventory load flag set. Loading shadows now (synchronously).");
			try {
				loadShadows(); // Use standard config method
				log.debug("POST-LOAD CHECK (after GameTick sync): reservedSlots map contents = {}", reservedSlots);
			} catch (Exception e) { log.error("Exception occurred during synchronous loadShadows execution in onGameTick", e); }
		}
	}

	/**
	 * Handles changes to the plugin's configuration panel.
	 * Updates manual shadows based on slotXItem changes.
	 * Clears specific shadows based on clearSlotNumberInput changes.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) { return; }

		String key = event.getKey();
		String newValue = event.getNewValue();

		// Handle manual slot item changes
		if (key.startsWith("slot") && key.endsWith("Item")) {
			log.debug("Manual config changed for key: {}, updating manual shadows.", key);
			clientThread.invokeLater(this::updateManualShadowsFromConfig);
		}
		// Handle the clear specific slot input change
		else if (key.equals("clearSlotNumberInput")) {
			// Check if the new value is a valid number
			if (newValue != null && !newValue.trim().isEmpty()) {
				log.debug("clearSlotNumberInput changed to: '{}'", newValue);
				try {
					int slotToClear = Integer.parseInt(newValue.trim());

					// Validate slot number
					if (slotToClear >= 0 && slotToClear < 28) {
						log.info("Attempting to clear shadows for slot {} via config input.", slotToClear);
						boolean autoChanged = false;
						boolean manualExisted = false;

						// Remove from automatic shadows map if present
						if (reservedSlots.remove(slotToClear) != null) {
							log.debug("Removed automatic shadow from slot {}", slotToClear);
							autoChanged = true;
						}
						// Remove from manual shadows map if present
						if (manualShadows.remove(slotToClear) != null) {
							log.debug("Removed manual shadow from slot {}", slotToClear);
							manualExisted = true; // Flag that manual existed, even if config clear fails
							// Also clear the corresponding manual config entry for that slot
							String manualConfigKey = "slot" + slotToClear + "Item";
							try {
								configManager.setConfiguration(CONFIG_GROUP, manualConfigKey, "");
								log.debug("Cleared manual config for key {}", manualConfigKey);
							} catch (Exception e) { log.error("Failed to clear manual config for key {}", manualConfigKey, e); }
						}

						// Save automatic shadows if they were changed
						if (autoChanged) {
							saveShadows();
						}

						// Provide feedback if anything was actually cleared
						if (autoChanged || manualExisted) {
							sendChatMessage("PerryVentory shadow cleared from slot " + (slotToClear + 1) + " via config.");
						} else {
							sendChatMessage("No PerryVentory shadow found to clear in slot " + (slotToClear + 1) + ".");
						}

					} else {
						log.warn("Invalid slot number entered in clearSlotNumberInput: {}. Must be 0-27.", slotToClear);
						sendChatMessage("Invalid slot number: " + slotToClear + ". Please enter 0-27.");
					}
				} catch (NumberFormatException e) {
					log.warn("Invalid input in clearSlotNumberInput: '{}' is not a number.", newValue);
					sendChatMessage("Invalid input: '" + newValue + "'. Please enter a number (0-27).");
				} finally {
					// Always reset the input field back to empty immediately after processing
					// Use invokeLater to avoid issues modifying config during event handling
					clientThread.invokeLater(() -> {
						try {
							log.trace("Resetting clearSlotNumberInput config value.");
							// Use unsetConfiguration for resetting to default (empty string)
							configManager.unsetConfiguration(CONFIG_GROUP, "clearSlotNumberInput");
						} catch(Exception e) {
							log.warn("Failed to reset clearSlotNumberInput state", e);
						}
					});
				}
			}
		}
	}


	/** Processes automatic inventory changes if initial state is ready. */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
			if (!previousInventoryState.isEmpty()) {
				log.trace("Queueing processing for ItemContainerChanged.");
				clientThread.invokeLater(() -> processInventoryChange(event));
			} else {
				log.trace("Skipping ItemContainerChanged processing because previous state is empty.");
			}
		}
	}

	/** Adds a 'Clear PerryVentory Shadow' menu option using onMenuOpened. */
	@Subscribe
	public void onMenuOpened(MenuOpened menuOpened) {
		MenuEntry[] entries = client.getMenuEntries();
		if (entries.length == 0) return;

		int inventorySlotContext = -1;
		MenuEntry anchorEntry = null;

		for (MenuEntry entry : entries) {
			int interfaceId = WidgetUtil.componentToInterface(entry.getParam1());
			if (interfaceId == InterfaceID.INVENTORY) {
				if (entry.getOption().equals(TARGET_OPTION)) {
					inventorySlotContext = entry.getParam0();
					anchorEntry = entry;
					log.trace("Found inventory slot context: Slot {}", inventorySlotContext);
					break;
				}
			}
		}

		if (anchorEntry != null && inventorySlotContext != -1 &&
				(reservedSlots.containsKey(inventorySlotContext) || manualShadows.containsKey(inventorySlotContext))) {
			log.debug("Adding Clear Shadow option for slot {}", inventorySlotContext);
			client.createMenuEntry(0)
					.setOption(CLEAR_SHADOW_OPTION)
					.setTarget(anchorEntry.getTarget())
					.setType(MenuAction.RUNELITE)
					.setParam0(inventorySlotContext)
					.setParam1(anchorEntry.getParam1());
		}
	}


	/** Handles the click action for the 'Clear PerryVentory Shadow' menu option. */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (event.getMenuOption().equals(CLEAR_SHADOW_OPTION) && event.getParam1() == WidgetInfo.INVENTORY.getId()) {
			int slot = event.getParam0();
			log.debug("Clear shadow option clicked for slot {}", slot);

			boolean autoChanged = false;
			if (reservedSlots.remove(slot) != null) {
				log.debug("Removed automatic shadow from slot {}", slot);
				autoChanged = true;
			}
			if (manualShadows.remove(slot) != null) {
				log.debug("Removed manual shadow from slot {}", slot);
				String configKey = "slot" + slot + "Item";
				try {
					configManager.setConfiguration(CONFIG_GROUP, configKey, "");
					log.debug("Cleared manual config for key {}", configKey);
				} catch (Exception e) { log.error("Failed to clear manual config for key {}", configKey, e); }
			}

			if (autoChanged) {
				saveShadows(); // Save if automatic state changed
			}
			sendChatMessage("PerryVentory shadow cleared from slot " + (slot + 1) + ".");
		}
	}


	// --- Manual Shadow Configuration Handling ---
	private void updateManualShadowsFromConfig() {
		manualShadows.clear();
		log.debug("Updating MANUAL reserved slots from configuration (expecting Item IDs)...");
		Supplier<String>[] configGetters = new Supplier[]{
				config::slot0Item, config::slot1Item, config::slot2Item, config::slot3Item,
				config::slot4Item, config::slot5Item, config::slot6Item, config::slot7Item,
				config::slot8Item, config::slot9Item, config::slot10Item, config::slot11Item,
				config::slot12Item, config::slot13Item, config::slot14Item, config::slot15Item,
				config::slot16Item, config::slot17Item, config::slot18Item, config::slot19Item,
				config::slot20Item, config::slot21Item, config::slot22Item, config::slot23Item,
				config::slot24Item, config::slot25Item, config::slot26Item, config::slot27Item
		};
		for (int slot = 0; slot < configGetters.length; slot++) {
			String itemIdString = configGetters[slot].get();
			if (itemIdString != null && !itemIdString.trim().isEmpty()) {
				try {
					int itemId = Integer.parseInt(itemIdString.trim());
					if (itemId > 0) {
						manualShadows.put(slot, itemId);
						log.debug("Manual config: Slot {} -> Parsed Item ID: {}", slot, itemId);
					} else {
						log.warn("Ignoring non-positive Item ID '{}' configured for slot {}", itemIdString, slot);
					}
				} catch (NumberFormatException e) {
					log.warn("Invalid manual config for slot {}: '{}' is not a valid number (Item ID). Please enter a numeric ID.", slot, itemIdString);
				}
			}
		}
		log.debug("Manual reserved slots map updated: {} entries", manualShadows.size());
	}

	// --- Automatic Shadow Logic ---
	private void processInventoryChange(ItemContainerChanged event) {
		ItemContainer currentContainer = event.getItemContainer();
		if (currentContainer == null) { log.warn("processInventoryChange called with null container for event: {}", event); return; }
		Item[] currentItems = currentContainer.getItems();
		log.debug("Processing inventory change. Current items: {}", Arrays.toString(currentItems));
		log.debug("Processing inventory change. Previous state: {}", previousInventoryState);

		boolean stateChanged = false;
		if (currentItems == null) { log.warn("processInventoryChange called with null items array for event: {}", event); return; }
		int inventorySize = currentContainer.size();

		// Phase 1: Analyze Changes
		Map<Integer, Integer> removedItems = new HashMap<>();
		Map<Integer, Integer> addedItems = new HashMap<>();
		Set<Integer> movedItemIds = new HashSet<>();
		for (int slot = 0; slot < inventorySize; slot++) {
			int previousItemId = previousInventoryState.getOrDefault(slot, -1);
			int currentItemId = (slot < currentItems.length && currentItems[slot] != null) ? currentItems[slot].getId() : -1;
			if (previousItemId != currentItemId) {
				if (previousItemId != -1) removedItems.put(slot, previousItemId);
				if (currentItemId != -1) addedItems.put(slot, currentItemId);
			}
		}
		log.trace("Phase 1 - Analysing Moves: RemovedMap={}, AddedMap={}", removedItems, addedItems);
		for (Map.Entry<Integer, Integer> removedEntry : removedItems.entrySet()) {
			int removedId = removedEntry.getValue();
			boolean wasMoved = false;
			for(Map.Entry<Integer, Integer> addedEntry : addedItems.entrySet()) {
				if (addedEntry.getValue().equals(removedId)) { wasMoved = true; break; }
			}
			if(wasMoved) { movedItemIds.add(removedId); }
		}
		log.trace("Phase 1 - Final MovedIDs set for this event: {}", movedItemIds);
		log.trace("Phase 1 Results: Removed={}, Added={}, MovedIDs={}", removedItems, addedItems, movedItemIds);

		// Phase 2: Process Added Items (Remove matching AUTOMATIC shadows)
		Iterator<Map.Entry<Integer, Integer>> addedIterator = addedItems.entrySet().iterator();
		while (addedIterator.hasNext()) {
			Map.Entry<Integer, Integer> addedEntry = addedIterator.next();
			int addedItemId = addedEntry.getValue();
			if (movedItemIds.contains(addedItemId)) { continue; }
			int idToCheck = addedItemId;
			try { // Normalize Coins
				ItemComposition definition = itemManager.getItemComposition(idToCheck);
				if (definition != null && definition.getName().equalsIgnoreCase("Coins")) { idToCheck = COINS; }
			} catch (Exception e) { log.error("Error looking up item def for added ID {}: {}", idToCheck, e.getMessage()); }

			int shadowSlotToRemove = -1;
			for (Map.Entry<Integer, Integer> shadowEntry : reservedSlots.entrySet()) { // Check automatic map
				if (shadowEntry.getValue().equals(idToCheck)) { shadowSlotToRemove = shadowEntry.getKey(); break; }
			}
			if (shadowSlotToRemove != -1) {
				if (reservedSlots.remove(shadowSlotToRemove) != null) {
					log.debug("Removed AUTOMATIC shadow state for item ID {} from slot {}", idToCheck, shadowSlotToRemove);
					stateChanged = true;
				}
			}
		}

		// Phase 3: Process Removed Items (Add AUTOMATIC shadows)
		Iterator<Map.Entry<Integer, Integer>> removedIterator = removedItems.entrySet().iterator();
		while (removedIterator.hasNext()) {
			Map.Entry<Integer, Integer> removedEntry = removedIterator.next();
			int removedItemId = removedEntry.getValue();
			int removedSlot = removedEntry.getKey();
			if (movedItemIds.contains(removedItemId)) { // Skip moved items
				log.trace("Skipping automatic shadow add for removed item {} from slot {} because it was moved.", removedItemId, removedSlot);
				continue;
			}
			int shadowItemId = removedItemId;
			try { // Normalize Coins
				ItemComposition definition = itemManager.getItemComposition(shadowItemId);
				if (definition != null && definition.getName().equalsIgnoreCase("Coins")) { shadowItemId = COINS; }
			} catch (Exception e) { log.error("Error looking up item def for removed ID {}: {}", shadowItemId, e.getMessage()); }

			boolean slotNowEmpty = !(removedSlot < currentItems.length && currentItems[removedSlot] != null && currentItems[removedSlot].getId() != -1);
			if(slotNowEmpty) {
				reservedSlots.put(removedSlot, shadowItemId); // Add to automatic map
				log.debug("Added AUTOMATIC shadow state for item ID {} to slot {}", shadowItemId, removedSlot);
				stateChanged = true;
			} else {
				log.debug("Skipped adding automatic shadow for removed item {} slot {} because slot was immediately filled.", shadowItemId, removedSlot);
			}
		}

		// Phase 4: Handle Displaced AUTOMATIC Shadows
		log.debug("--- Checking Displaced AUTOMATIC Shadows ---");
		List<Integer> shadowsToReassign = new ArrayList<>();
		Iterator<Map.Entry<Integer, Integer>> autoShadowIterator = reservedSlots.entrySet().iterator();
		Item[] itemsAfterChanges = currentContainer.getItems();
		while (autoShadowIterator.hasNext()) {
			Map.Entry<Integer, Integer> entry = autoShadowIterator.next();
			int slot = entry.getKey();
			int shadowItemId = entry.getValue();
			boolean slotOccupiedByRealItem = (slot < itemsAfterChanges.length && itemsAfterChanges[slot] != null && itemsAfterChanges[slot].getId() != -1);
			if (slotOccupiedByRealItem) {
				log.debug("Phase 4: Displacing AUTOMATIC shadow in slot {} (by real item). Item ID: {}", slot, shadowItemId);
				try {
					autoShadowIterator.remove(); shadowsToReassign.add(shadowItemId); stateChanged = true;
				} catch (Exception e) { log.error("Phase 4: Error removing displaced automatic shadow", e); }
			}
		}
		boolean relocationEnabled = true; // Assume true, or read from config
		if (relocationEnabled && !shadowsToReassign.isEmpty()) {
			log.debug("Phase 4: Trying to relocate {} displaced AUTOMATIC shadows.", shadowsToReassign.size());
			Item[] latestItems = client.getItemContainer(InventoryID.INVENTORY).getItems();
			if (latestItems != null) {
				for (int itemIdToPlace : shadowsToReassign) {
					boolean placed = tryReassignShadow(itemIdToPlace, latestItems, reservedSlots, inventorySize);
					if (!placed) { log.warn("Phase 4: Relocation failed for automatic shadow item ID {}.", itemIdToPlace); }
				}
			} else { log.error("Phase 4: Cannot relocate automatic shadows, failed to get current inventory items."); }
		} else if (!shadowsToReassign.isEmpty()) {
			log.debug("Phase 4: Relocation disabled or no shadows to relocate. {} displaced automatic shadows lost.", shadowsToReassign.size());
		}

		// Phase 5: Update Previous State
		updatePreviousInventoryState(currentItems);

		// Save Shadows if Automatic State Changed (Save frequently)
		if (stateChanged) {
			log.debug("Automatic shadow state changed. Saving shadows now...");
			saveShadows(); // Call standard save method
		} else {
			log.trace("No changes to automatic shadow state detected that require saving.");
		}
	}

	// Helper for relocating AUTOMATIC shadows (only checks automatic map)
	private boolean tryReassignShadow(int shadowItemId, Item[] currentItems, Map<Integer, Integer> currentReservedSlots, int inventorySize) {
		for (int slot = 0; slot < inventorySize; slot++) {
			boolean slotIsEmptyOfRealItem = !(slot < currentItems.length && currentItems[slot] != null && currentItems[slot].getId() != -1);
			boolean slotIsEmptyOfAutoShadow = !currentReservedSlots.containsKey(slot);
			if (slotIsEmptyOfRealItem && slotIsEmptyOfAutoShadow) {
				currentReservedSlots.put(slot, shadowItemId);
				log.debug("Relocated AUTOMATIC shadow for item ID {} to available slot {}", shadowItemId, slot);
				return true;
			}
		}
		log.debug("Could not find suitable empty slot to relocate shadow ID {}", shadowItemId);
		return false; // Ensure return false exists
	}


	// --- State Initialization and Update Helpers ---
	private void initializeInventoryState() {
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory != null) {
			log.debug("Initializing previous inventory state...");
			updatePreviousInventoryState(inventory.getItems());
			log.debug("Initialized previous inventory state. Current state: {}", previousInventoryState);
		} else {
			previousInventoryState.clear();
			log.warn("Could not initialize inventory state (called from WidgetLoaded) - inventory container not found.");
		}
	}

	private void updatePreviousInventoryState(Item[] items) {
		previousInventoryState.clear();
		if (items == null) { log.warn("updatePreviousInventoryState called with null items array."); return; }
		for (int slot = 0; slot < items.length; slot++) {
			if (items[slot] != null && items[slot].getId() != -1) {
				previousInventoryState.put(slot, items[slot].getId());
			}
		}
		log.trace("Updated previousInventoryState: {}", previousInventoryState);
	}

	// --- Persistence Methods (Using Standard Config) ---
	private void loadShadows() {
		reservedSlots.clear();
		String json = null;
		log.debug("Attempting to load shadows using configManager.getConfiguration for key: {}", SHADOW_DATA_KEY);
		try {
			json = configManager.getConfiguration(CONFIG_GROUP, SHADOW_DATA_KEY);
		} catch (Exception e) { log.error("Error reading shadow data using getConfiguration", e); return; }

		if (json != null && !json.isEmpty()) {
			log.debug("Loaded shadows json (from standard config): {}", json);
			try {
				if (gson == null) gson = new Gson();
				Map<Integer, Integer> loaded = gson.fromJson(json, SHADOW_MAP_TYPE);
				if (loaded != null) {
					reservedSlots.putAll(loaded);
					log.info("Loaded {} shadows from standard config.", loaded.size());
				} else { log.warn("Deserialization of standard config shadows resulted in a null map."); }
			} catch (Exception e) { log.error("Failed to parse shadows json from standard config", e); }
		} else {
			log.debug("No previous shadow data found in standard config for key {}.", SHADOW_DATA_KEY);
		}
	}

	private void saveShadows() {
		if (reservedSlots.isEmpty()) {
			log.debug("Shadow map empty. Unsetting standard configuration for key: {}", SHADOW_DATA_KEY);
			try {
				configManager.unsetConfiguration(CONFIG_GROUP, SHADOW_DATA_KEY);
			} catch (Exception e) { log.error("Error unsetting shadow data standard config", e); }
		} else {
			log.debug("Attempting to save {} shadows to standard configuration...", reservedSlots.size());
			if (gson == null) { log.error("Gson is null, cannot save shadows!"); return; }
			try {
				String json = gson.toJson(reservedSlots);
				log.debug("Saving shadows json to standard config: {}", json);
				configManager.setConfiguration(CONFIG_GROUP, SHADOW_DATA_KEY, json);
				log.debug("Saved {} shadows to standard config.", reservedSlots.size());
			} catch (Exception e) { log.error("Error saving shadow data to standard config. Map size: {}", reservedSlots.size(), e); }
		}
	}

	// --- Utility / Action Methods ---
	// Removed: public void clearAllShadowsButton() // No longer linked to config button

	/** Clears ALL shadows (Automatic and Manual) */
	public void clearAllShadows() { // Keep public if might be called externally later
		clientThread.invokeLater(() -> { // Ensure thread safety for map/config access
			boolean changed = false;
			// Clear automatic shadows
			if (!reservedSlots.isEmpty()) {
				reservedSlots.clear();
				saveShadows(); // Persist cleared automatic state
				changed = true;
				log.info("Cleared all automatic shadows.");
			}
			// Clear manual shadows
			boolean manualCleared = false;
			if (!manualShadows.isEmpty()) {
				manualShadows.clear(); // Clear runtime map
				manualCleared = true;
			}
			// Clear manual config entries
			for (int i=0; i<28; i++) {
				String keyName = "slot" + i + "Item";
				if (configManager.getConfiguration(CONFIG_GROUP, keyName) != null && !configManager.getConfiguration(CONFIG_GROUP, keyName).isEmpty()) {
					configManager.unsetConfiguration(CONFIG_GROUP, keyName);
					manualCleared = true;
				}
			}
			// Also clear the specific slot input field
			if (configManager.getConfiguration(CONFIG_GROUP, "clearSlotNumberInput") != null && !configManager.getConfiguration(CONFIG_GROUP, "clearSlotNumberInput").isEmpty()) {
				configManager.unsetConfiguration(CONFIG_GROUP, "clearSlotNumberInput");
				manualCleared = true; // Count this as manual change for logging
			}

			if(manualCleared) {
				log.info("Cleared all manual shadows and config entries.");
				changed = true; // Ensure notification if manual was cleared
			}
			// Notify user
			String message = changed ? "All PerryVentory shadows cleared." : "No PerryVentory shadows to clear.";
			sendChatMessage(message);
		});
	}

	private void sendChatMessage(String message) {
		final String notification = new ChatMessageBuilder()
				.append(ChatColorType.NORMAL).append(message)
				.build();
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", notification, "");
	}

} // End of class PerryVentoryPlugin