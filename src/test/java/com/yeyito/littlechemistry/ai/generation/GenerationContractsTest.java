package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.content.DynamicContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GenerationContractsTest {
	@Test
	void generalBlockAssemblyGuidanceIsDeliveredOnlyByTheFocusedBlockContract() {
		String block = contract("reference/contracts/block.md");
		assertTrue(block.contains("DynamicBlockAssembly"));
		assertTrue(block.contains("same face-connected footprint"));
		assertTrue(block.contains("collision"));

		String initial = ContentGenerationAgent.SYSTEM_PROMPT + GenerationRequest.fixed(
				DynamicContentType.BLOCK, null, "Wide Gate", 1, null).userPrompt();
		assertFalse(initial.contains("DynamicBlockAssembly"));
		assertFalse(initial.contains("face-connected footprint"));
	}

	@Test
	void recoverableSlotDefaultsRemainFocusedWithoutRemovingExplicitPolicyFreedom() {
		String workstation = contract("reference/contracts/workstation.md");
		assertTrue(workstation.contains("shorter constructor"));
		assertTrue(workstation.contains("remain extractable"));
		assertTrue(workstation.contains("full boolean constructor"));
		assertTrue(workstation.contains("intentional permanent slot rule"));
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.contains("allowPlayerExtract"));
	}

	private static String contract(String path) {
		return GenerationContracts.documents().stream()
				.filter(contract -> contract.path().equals(path))
				.findFirst().orElseThrow().content();
	}
}
