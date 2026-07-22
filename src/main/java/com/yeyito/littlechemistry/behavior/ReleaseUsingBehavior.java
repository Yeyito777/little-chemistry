package com.yeyito.littlechemistry.behavior;

/** Invoked when the user releases a generated item's use button; true accepts the release action. */
public interface ReleaseUsingBehavior extends DynamicBehavior {
	boolean releaseUsing(DynamicItemUsingContext context);
}
