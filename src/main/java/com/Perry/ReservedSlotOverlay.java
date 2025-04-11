package com.Perry;

import com.google.inject.Inject;
import net.runelite.api.Client;
// ItemID likely not needed directly here
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
// Import config
import com.Perry.PerryVentoryConfig;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

public class ReservedSlotOverlay extends Overlay {

    private final PerryVentoryPlugin plugin; // Changed type back
    private final ItemManager itemManager;
    private final Client client;
    private final PerryVentoryConfig config; // Inject config

    @Inject
    // Constructor needs the config injected
    public ReservedSlotOverlay(PerryVentoryPlugin plugin, ItemManager itemManager, Client client, PerryVentoryConfig config) { // Changed type back and added config
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.client = client;
        this.config = config; // Store config
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!isInventoryOpen()) {
            return null;
        }

        // Access HashMap directly is okay now panel is gone
        Map<Integer, Integer> reservedSlots = plugin.getReservedSlots();

        for (Map.Entry<Integer, Integer> entry : reservedSlots.entrySet()) {
            int slot = entry.getKey();
            int itemId = entry.getValue();

            BufferedImage itemImage = itemManager.getImage(itemId);
            if (itemImage != null) {
                Point slotLocation = getInventorySlotLocation(slot);
                if (slotLocation == null) {
                    continue;
                }

                // *** Use Color and Alpha from Config ***
                Color shadowColor = config.shadowColor();
                // Extract alpha for transparency (0.0f to 1.0f)
                float alpha = Math.max(0.0f, Math.min(1.0f, shadowColor.getAlpha() / 255.0f)); // Clamp alpha

                Composite originalComposite = graphics.getComposite();
                // Set composite based on configured alpha
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                // Draw the item image with the calculated transparency
                graphics.drawImage(itemImage, slotLocation.x, slotLocation.y, null);

                // Restore original composite
                graphics.setComposite(originalComposite);
            }
        }
        return null;
    }

    private boolean isInventoryOpen() {
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        return inventoryWidget != null && !inventoryWidget.isHidden();
    }

    private Point getInventorySlotLocation(int slot) {
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        if (inventoryWidget == null || inventoryWidget.getChildren() == null) { return null; }
        Widget[] children = inventoryWidget.getChildren();
        if (slot < 0 || slot >= children.length) { return null; }
        Widget itemWidget = children[slot];
        if (itemWidget == null || itemWidget.getCanvasLocation() == null) { return null; }
        net.runelite.api.Point canvasLocation = itemWidget.getCanvasLocation();
        return new java.awt.Point(canvasLocation.getX(), canvasLocation.getY());
    }
}