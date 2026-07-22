package com.yeyito.littlechemistry.behavior;

/** Server-owned lifecycle of the recipe associated with a placed workstation. */
public enum WorkstationRecipeStatus {
	/** No captured or cached recipe currently applies. */
	NONE,
	/** An AI recipe job is in progress. */
	GENERATING,
	/** A generated recipe is available to process. */
	READY,
	/** The current recipe signature was intentionally rejected and has a non-craftable explanation. */
	REJECTED,
	/** Processing finished but the result cannot currently be inserted. */
	BLOCKED,
	/** Recipe generation or validation failed without disabling the workstation behavior. */
	FAILED
}
