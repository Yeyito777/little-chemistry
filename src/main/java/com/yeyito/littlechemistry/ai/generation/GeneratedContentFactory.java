package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.content.GeneratedContentSpec;

/**
 * Entry point implemented by a world-owned generated content class.
 *
 * <p>The generalist generation agent writes an ordinary Java implementation into the world's source workspace.
 * Verification compiles that source, supplies the separately authored and compiled behavior source, and asks the
 * factory to construct the complete runtime definition. Keeping this contract deliberately small lets generated
 * code use every public Little Chemistry or Minecraft API without growing a tool-specific shadow API.</p>
 */
public interface GeneratedContentFactory {
	GeneratedContentSpec create(String behaviorSource) throws Exception;
}
