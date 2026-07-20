package com.yeyito.littlechemistry.behavior;

/** Optional server-tick hook for a generated workstation. */
public interface WorkstationTickBehavior extends DynamicBehavior {
	void workstationTick(DynamicWorkstationContext context);
}
