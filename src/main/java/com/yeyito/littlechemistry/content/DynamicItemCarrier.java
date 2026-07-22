package com.yeyito.littlechemistry.content;

/**
 * Marks a bootstrap-registered item whose stacks represent logical generated content.
 *
 * <p>The marker deliberately does not prescribe an {@link net.minecraft.world.item.Item}
 * superclass. Native Minecraft archetypes keep their real superclass while the shared stack
 * dispatch layer supplies the optional generated callbacks.</p>
 */
public interface DynamicItemCarrier {
}
