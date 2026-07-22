package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.crafting.WorkstationRecipeRejection;

import java.io.IOException;

/** Internal terminal control flow for a valid, verified workstation recipe rejection. */
final class RecipeRejectedException extends IOException {
	private final WorkstationRecipeRejection rejection;

	RecipeRejectedException(WorkstationRecipeRejection rejection) {
		super(rejection.description());
		this.rejection = rejection;
	}

	WorkstationRecipeRejection rejection() {
		return rejection;
	}
}
