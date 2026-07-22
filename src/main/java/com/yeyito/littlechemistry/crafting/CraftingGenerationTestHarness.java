package com.yeyito.littlechemistry.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Places and starts every crafting-grid case in one recorded generation test suite. */
public final class CraftingGenerationTestHarness {
	private static final int FRONT_DISTANCE = 4;

	private CraftingGenerationTestHarness() {
	}

	public static RunResult run(ServerPlayer player, int suiteNumber) throws IOException {
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager == null || !manager.belongsTo(player.level())) {
			throw new IOException("AI crafting is not available in this world yet");
		}
		CraftingGenerationTestSuite suite = CraftingGenerationTestSuite.load(suiteNumber);
		ServerLevel level = player.level();
		List<BlockPos> positions = linePositions(player.blockPosition(), player.getDirection(), suite.recipes().size());
		List<List<ItemStack>> grids = resolveGrids(suite);
		List<BlockState> replacedStates = new ArrayList<>(positions.size());
		for (BlockPos position : positions) {
			if (!level.getWorldBorder().isWithinBounds(position)) {
				throw new IOException("The test row would cross the world border at " + position.toShortString());
			}
			BlockState replaced = level.getBlockState(position);
			if (!replaced.canBeReplaced()) {
				throw new IOException("Clear the test row first; " + position.toShortString() + " is occupied by "
						+ replaced.getBlock().getName().getString());
			}
			replacedStates.add(replaced);
		}

		int placed = 0;
		try {
			for (int index = 0; index < positions.size(); index++) {
				if (!level.setBlock(positions.get(index), Blocks.CRAFTING_TABLE.defaultBlockState(), 3)) {
					throw new IOException("Could not place a crafting table at " + positions.get(index).toShortString());
				}
				placed++;
			}
		} catch (IOException | RuntimeException failure) {
			for (int index = placed - 1; index >= 0; index--) {
				level.setBlock(positions.get(index), replacedStates.get(index), 3);
			}
			throw failure;
		}

		List<CaseResult> cases = new ArrayList<>(suite.recipes().size());
		for (int index = 0; index < suite.recipes().size(); index++) {
			CraftingGenerationTestSuite.RecipeCase recipe = suite.recipes().get(index);
			SharedCraftingContainer table = manager.table(level, positions.get(index));
			List<ItemStack> grid = grids.get(index);
			for (int slot = 0; slot < grid.size(); slot++) {
				if (!grid.get(slot).isEmpty()) table.setItem(slot, grid.get(slot));
			}
			boolean started = manager.requestRecipe(player, table);
			cases.add(new CaseResult(recipe.label(), positions.get(index), started));
		}
		return new RunResult(suite.number(), suite.name(), List.copyOf(cases));
	}

	static List<BlockPos> linePositions(BlockPos playerPosition, Direction facing, int count) {
		if (count < 1) return List.of();
		Direction forward = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
		Direction right = forward.getClockWise();
		BlockPos center = playerPosition.relative(forward, FRONT_DISTANCE);
		int firstOffset = -(count / 2);
		List<BlockPos> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			result.add(center.relative(right, firstOffset + index));
		}
		return List.copyOf(result);
	}

	private static List<List<ItemStack>> resolveGrids(CraftingGenerationTestSuite suite) throws IOException {
		List<List<ItemStack>> result = new ArrayList<>(suite.recipes().size());
		for (CraftingGenerationTestSuite.RecipeCase recipe : suite.recipes()) {
			List<ItemStack> grid = new ArrayList<>(9);
			for (int slot = 0; slot < 9; slot++) grid.add(ItemStack.EMPTY);
			for (int y = 0; y < recipe.height(); y++) {
				for (int x = 0; x < recipe.width(); x++) {
					var id = recipe.ingredients().get(y * recipe.width() + x);
					if (id == null) continue;
					Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
					if (item == null || item == Items.AIR) {
						throw new IOException("Test recipe '" + recipe.label() + "' references unknown item " + id);
					}
					grid.set(y * 3 + x, new ItemStack(item));
				}
			}
			result.add(List.copyOf(grid));
		}
		return List.copyOf(result);
	}

	public record CaseResult(String label, BlockPos position, boolean started) {
	}

	public record RunResult(int suiteNumber, String suiteName, List<CaseResult> cases) {
		public int placedCount() {
			return cases.size();
		}

		public long startedCount() {
			return cases.stream().filter(CaseResult::started).count();
		}
	}
}
