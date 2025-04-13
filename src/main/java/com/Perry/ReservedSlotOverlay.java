package com.Perry;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

@Slf4j
public class ReservedSlotOverlay extends Overlay {

    private final PerryVentoryPlugin plugin;
    private final ItemManager itemManager;
    private final Client client;
    private final PerryVentoryConfig config;

    @Inject
    public ReservedSlotOverlay(PerryVentoryPlugin plugin, ItemManager itemManager, Client client, PerryVentoryConfig config) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !isInventoryOpen()) {
            log.trace("Overlay skipped: Not logged in or inventory closed.");
            return null;
        }

        // Get both maps from the plugin
        Map<Integer, Integer> autoShadows = plugin.getReservedSlots();
        Map<Integer, Integer> manualShadows = plugin.getManualShadows();

        if (itemManager == null || config == null) {
            log.error("Overlay skipped: Null dependency (itemManager or config).");
            return null;
        }
        boolean hasAuto = autoShadows != null && !autoShadows.isEmpty();
        boolean hasManual = manualShadows != null && !manualShadows.isEmpty();

        if (!hasAuto && !hasManual) {
            log.trace("Overlay skipped: No automatic or manual shadows to render.");
            return null;
        }

        log.trace("Starting overlay render loop. Auto: {}, Manual: {}",
                (hasAuto ? autoShadows.size() : 0),
                (hasManual ? manualShadows.size() : 0));

        // Prepare graphics settings
        Color shadowColor = config.shadowColor();
        if (shadowColor == null) {
            log.error("Overlay render: config.shadowColor() returned null! Using fallback.");
            shadowColor = new Color(0,0,0,128);
        }
        float alpha = Math.max(0.0f, Math.min(1.0f, shadowColor.getAlpha() / 255.0f));
        Composite originalComposite = graphics.getComposite();
        AlphaComposite alphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);

        // --- Draw Automatic Shadows ---
        if (hasAuto) {
            log.trace("Rendering automatic shadows...");
            drawShadowsFromMap(graphics, autoShadows, alphaComposite, originalComposite);
        }

        // --- Draw Manual Shadows ---
        if (hasManual) {
            log.trace("Rendering manual shadows...");
            drawShadowsFromMap(graphics, manualShadows, alphaComposite, originalComposite);
        }

        // Ensure composite is restored if loops didn't run or finished
        graphics.setComposite(originalComposite);

        log.trace("Overlay render loop finished.");
        return null;
    }

    /** Helper method to draw shadows from a given map */
    private void drawShadowsFromMap(Graphics2D graphics, Map<Integer, Integer> shadowMap, AlphaComposite alphaComposite, Composite originalComposite) {
        for (Map.Entry<Integer, Integer> entry : shadowMap.entrySet()) {
            int slot = entry.getKey();
            int itemId = entry.getValue();
            BufferedImage itemImage = itemManager.getImage(itemId);

            if (itemImage != null) {
                Point slotLocation = getInventorySlotLocation(slot);
                if (slotLocation == null) {
                    continue; // Skip if location invalid
                }

                graphics.setComposite(alphaComposite); // Apply transparency
                try {
                    graphics.drawImage(itemImage, slotLocation.x, slotLocation.y, null);
                } catch (Exception e) { log.error("Overlay render: Exception during graphics.drawImage() for item {} slot {}", itemId, slot, e); }
                // Restore composite inside loop is safer if needed, but setComposite is relatively cheap
                // graphics.setComposite(originalComposite);

            } else {
                log.warn("Overlay render: BufferedImage was null for item ID {} in slot {}", itemId, slot);
            }
        }
        // Restore composite after finishing this map's loop
        graphics.setComposite(originalComposite);
    }


    /**
     * Checks if the main inventory widget is currently visible.
     */
    private boolean isInventoryOpen() {
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        // Return true only if the widget exists AND is not hidden
        return inventoryWidget != null && !inventoryWidget.isHidden();
    }

    /**
     * Gets the top-left screen coordinates for a specific inventory slot.
     * Returns null if the location is invalid. Includes debug logging.
     */
    private Point getInventorySlotLocation(int slot) {
        log.trace("getInventorySlotLocation: Called for slot {}", slot);

        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        if (inventoryWidget == null) {
            log.warn("getInventorySlotLocation: Inventory widget (WidgetInfo.INVENTORY) is NULL for slot {}", slot);
            return null;
        }
        log.trace("getInventorySlotLocation: Found inventory widget for slot {}", slot);

        Widget[] children = inventoryWidget.getChildren();
        if (children == null) {
            log.warn("getInventorySlotLocation: Inventory widget getChildren() returned NULL for slot {}", slot);
            return null;
        }

        if (slot < 0 || slot >= children.length) {
            log.error("getInventorySlotLocation: Slot index {} is OUT OF BOUNDS (Children length: {})", slot, children.length);
            return null;
        }
        log.trace("getInventorySlotLocation: Slot index {} is within bounds.", slot);

        Widget itemWidget = children[slot];
        if (itemWidget == null) {
            log.warn("getInventorySlotLocation: Child widget for slot {} is NULL.", slot);
            return null;
        }

        // Log properties for debugging
        log.debug("Slot {}: Hidden={}, ID={}, Type={}, ParentID={}, Size=({}x{}), RelPos=({}, {}), ItemID={}, ItemQty={}, SelfHidden={}",
                slot, itemWidget.isHidden(), itemWidget.getId(), itemWidget.getType(), itemWidget.getParentId(),
                itemWidget.getWidth(), itemWidget.getHeight(), itemWidget.getRelativeX(), itemWidget.getRelativeY(),
                itemWidget.getItemId(), itemWidget.getItemQuantity(), itemWidget.isSelfHidden()
        );

        net.runelite.api.Point canvasLocation = itemWidget.getCanvasLocation();
        if (canvasLocation == null) {
            log.warn("getInventorySlotLocation: itemWidget.getCanvasLocation() returned NULL for slot {}", slot);
            return null;
        }
        log.trace("getInventorySlotLocation: Found canvasLocation for slot {}: ({}, {})", slot, canvasLocation.getX(), canvasLocation.getY());

        int x = canvasLocation.getX();
        int y = canvasLocation.getY();
        if (x < 0 || y < 0) {
            // Keep as WARN during debugging, maybe change to TRACE later if it's just transient noise
            log.warn("getInventorySlotLocation: Slot {} reported invalid coords ({}, {}). Returning null.", slot, x, y);
            return null;
        }

        log.trace("getInventorySlotLocation: Returning valid Point ({}, {}) for slot {}", x, y, slot);
        return new java.awt.Point(x, y);
    }
}