package com.yeyito.littlechemistry.behavior;

import org.jspecify.annotations.Nullable;

/**
 * Core opt-in behavior for a generated block that acts as a workstation.
 *
 * <p>This callback describes which inventory slots make up the recipe currently offered by a placed workstation.
 * Returning {@code null} means the current inventory is not a valid recipe request. The runtime snapshots the named
	 * slots after this callback returns. This is a pure, read-only capture callback: the runtime rejects context or
	 * inventory mutation while it runs, and implementations must not retain the context or its stacks.</p>
 *
 * <p>Behavior objects are shared, stateless definition singletons. Per-placement progress and other mutable data must
 * be stored through {@link DynamicWorkstationContext}.</p>
 */
public interface WorkstationBehavior extends DynamicBehavior {
	@Nullable WorkstationRecipeRequest createWorkstationRecipe(DynamicWorkstationContext context);
}
