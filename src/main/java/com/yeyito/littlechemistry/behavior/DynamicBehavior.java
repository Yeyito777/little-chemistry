package com.yeyito.littlechemistry.behavior;

/**
 * Marker implemented by every separately loaded generated content class.
 *
 * <p>The registry creates one behavior object for a generated content definition and reuses it for every stack and
 * placed block carrying that definition. Implementations must therefore be stateless singletons: mutable state for a
 * particular stack, entity, or block position belongs in the supplied context or the underlying Minecraft object,
 * never in fields on the generated behavior object.</p>
 */
public interface DynamicBehavior {
}
