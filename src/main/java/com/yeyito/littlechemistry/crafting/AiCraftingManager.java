package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.AuthConfig;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.ai.generation.GenerationModel;
import com.yeyito.littlechemistry.ai.generation.RecipeGenerationAgent;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Owns physical and portable crafting grids, persistent AI recipes, and active recipe jobs. */
public final class AiCraftingManager {
	private static final int TABLES_FORMAT = 1;
	private static final int RECIPES_FORMAT = 3;
	private static final int MAX_ACTIVE_JOBS = 8;
	private static final String REASONING_EFFORT = "low";
	private static final ExecutorService GENERATION_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
	private static volatile AiCraftingManager active;

	private final MinecraftServer server;
	private final Path tablesFile;
	private final Path recipesFile;
	private final Map<TableKey, SharedCraftingContainer> tables = new HashMap<>();
	private final Map<RecipeSignature, AiCraftingRecipe> recipes = new LinkedHashMap<>();
	private final Map<SmeltingRecipeSignature, AiSmeltingRecipe> smeltingRecipes = new LinkedHashMap<>();
	private final Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipesByKey = new HashMap<>();
	private final Map<RecipeSignature, ActiveJob> jobs = new HashMap<>();
	private final Map<SmeltingRecipeSignature, ActiveSmeltingJob> smeltingJobs = new HashMap<>();
	private final Map<Container, SmeltingRecipeSignature> furnaceLocks = new IdentityHashMap<>();
	private boolean tablesDirty;
	private boolean recipesNeedRewrite;

	private AiCraftingManager(MinecraftServer server) throws IOException {
		this.server = server;
		Path directory = server.getWorldPath(LevelResource.ROOT).resolve("little-chemistry");
		this.tablesFile = directory.resolve("crafting-tables.json");
		this.recipesFile = directory.resolve("ai-recipes.json");
		loadTables();
		loadRecipes();
		if (pruneUnavailableRecipes()) recipesNeedRewrite = true;
		if (recipesNeedRewrite) saveRecipes(recipes, smeltingRecipes);
		rebuildRecipeIndex();
	}

	public static void start(MinecraftServer server) {
		try {
			AiCraftingManager manager = new AiCraftingManager(server);
			active = manager;
			LittleChemistry.LOGGER.info("Loaded {} shared crafting tables, {} AI crafting recipes, and {} AI smelting recipes",
					manager.tables.size(), manager.recipes.size(), manager.smeltingRecipes.size());
		} catch (IOException error) {
			throw new IllegalStateException("Could not load Little Chemistry crafting data", error);
		}
	}

	public static void stop(MinecraftServer server) {
		AiCraftingManager manager = active;
		if (manager == null || manager.server != server) return;
		active = null;
		for (ActiveJob job : List.copyOf(manager.jobs.values())) {
			job.promise.completeExceptionally(new IllegalStateException("Server stopped during recipe generation"));
			if (job.task != null) job.task.cancel(true);
			job.tables.forEach(table -> table.unlock(job.signature));
		}
		manager.jobs.clear();
		for (ActiveSmeltingJob job : List.copyOf(manager.smeltingJobs.values())) {
			job.promise.completeExceptionally(new IllegalStateException("Server stopped during recipe generation"));
			if (job.task != null) job.task.cancel(true);
		}
		manager.smeltingJobs.clear();
		manager.furnaceLocks.clear();
		try {
			manager.flushTables();
		} catch (IOException error) {
			LittleChemistry.LOGGER.error("Could not save shared crafting tables while stopping", error);
		}
	}

	public static AiCraftingManager active() {
		return active;
	}

	public boolean belongsTo(Level level) {
		return level.getServer() == server;
	}

	public boolean ownsRecipeManager(RecipeManager recipeManager) {
		return server.getRecipeManager() == recipeManager;
	}

	public static void tick(MinecraftServer server) {
		AiCraftingManager manager = active;
		if (manager == null || manager.server != server || !manager.tablesDirty) return;
		try {
			manager.flushTables();
		} catch (IOException error) {
			LittleChemistry.LOGGER.error("Could not save shared crafting table contents", error);
		}
	}

	public SharedCraftingContainer table(ServerLevel level, BlockPos pos) {
		TableKey key = TableKey.of(level, pos);
		return tables.computeIfAbsent(key, ignored -> SharedCraftingContainer.physical(this, key, List.of()));
	}

	public SharedCraftingContainer portableTable() {
		return SharedCraftingContainer.portable(this);
	}

	public boolean isLocked(ServerLevel level, BlockPos pos) {
		SharedCraftingContainer table = tables.get(TableKey.of(level, pos));
		return table != null && table.isLocked();
	}

	public Optional<RecipeHolder<CraftingRecipe>> findRecipe(CraftingInput input, Level level) {
		if (level.getServer() != server) return Optional.empty();
		RecipeSignature signature = RecipeSignature.fromInput(input);
		if (signature == null) return Optional.empty();
		AiCraftingRecipe recipe = recipes.get(signature);
		if (recipe == null) recipe = recipes.get(signature.mirrored());
		if (recipe == null || !recipe.outputAvailable()) return Optional.empty();
		return Optional.of(craftingHolder(recipe));
	}

	public Optional<RecipeHolder<SmeltingRecipe>> findSmeltingRecipe(SingleRecipeInput input, Level level) {
		if (level.getServer() != server) return Optional.empty();
		SmeltingRecipeSignature signature = SmeltingRecipeSignature.fromInput(input);
		if (signature == null) return Optional.empty();
		AiSmeltingRecipe recipe = smeltingRecipes.get(signature);
		if (recipe == null || !recipe.outputAvailable()) return Optional.empty();
		return Optional.of(smeltingHolder(recipe));
	}

	public boolean hasSmeltingRecipe(ItemStack stack, Level level) {
		return findSmeltingRecipe(new SingleRecipeInput(stack), level).isPresent();
	}

	public boolean hasAnySmeltingRecipe(ItemStack stack, Level level) {
		return belongsTo(level) && server.getRecipeManager()
				.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).isPresent();
	}

	public Optional<RecipeHolder<?>> findRecipeByKey(ResourceKey<Recipe<?>> key) {
		return Optional.ofNullable(recipesByKey.get(key));
	}

	public boolean isSmeltingLocked(Container furnace) {
		return furnaceLocks.containsKey(furnace);
	}

	public boolean requestRecipe(ServerPlayer player, SharedCraftingContainer table) {
		if (table.isLocked()) {
			player.sendSystemMessage(error("This crafting grid is already waiting for the AI."));
			return false;
		}
		RecipeSignature signature = RecipeSignature.capture(table);
		if (signature == null) {
			player.sendSystemMessage(error("Put ingredients in the crafting grid first."));
			return false;
		}
		if (server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, table.asCraftInput(), player.level()).isPresent()) {
			player.sendSystemMessage(error("That crafting grid already has a valid recipe."));
			return false;
		}

		ActiveJob existing = jobs.get(signature);
		if (existing == null) existing = jobs.get(signature.mirrored());
		if (existing == null && activeJobCount() >= MAX_ACTIVE_JOBS) {
			player.sendSystemMessage(error("The AI is busy inventing other recipes. Try again shortly."));
			return false;
		}
		table.lock(existing == null ? signature : existing.signature);
		if (existing != null) {
			existing.tables.add(table);
			existing.requesters.add(player.getUUID());
			return true;
		}

		ActiveJob job = new ActiveJob(signature);
		job.tables.add(table);
		job.requesters.add(player.getUUID());
		jobs.put(signature, job);
		JsonObject context = signature.toAiContext();
		try {
			job.task = submitGeneration(job.promise, context);
		} catch (Throwable submissionFailure) {
			jobs.remove(signature);
			table.unlock(signature);
			player.sendSystemMessage(error("Could not start the recipe generation job."));
			LittleChemistry.LOGGER.error("Could not submit a recipe generation job", submissionFailure);
			return false;
		}

		job.promise.whenComplete((generated, failure) -> {
			if (!server.isRunning()) return;
			server.execute(() -> complete(job, generated, failure));
		});
		return true;
	}

	public boolean requestSmeltingRecipe(ServerPlayer player, Container furnace) {
		if (!(furnace instanceof FurnaceBlockEntity furnaceEntity)
				|| !(player.containerMenu instanceof AiFurnaceMenuAccess access)
				|| access.littleChemistry$getFurnaceContainer() != furnace
				|| furnaceEntity.getLevel() != player.level()
				|| player.level().getBlockEntity(furnaceEntity.getBlockPos()) != furnaceEntity
				|| !furnace.stillValid(player)) {
			player.sendSystemMessage(error("That furnace is no longer available."));
			return false;
		}
		if (isSmeltingLocked(furnace)) {
			player.sendSystemMessage(error("This furnace is already waiting for the AI."));
			return false;
		}
		SmeltingRecipeSignature signature = SmeltingRecipeSignature.fromStack(furnace.getItem(0));
		if (signature == null) {
			player.sendSystemMessage(error("Put an ingredient in the furnace first."));
			return false;
		}
		SingleRecipeInput input = new SingleRecipeInput(furnace.getItem(0));
		if (server.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, player.level()).isPresent()) {
			player.sendSystemMessage(error("That ingredient already has a valid smelting recipe."));
			return false;
		}

		ActiveSmeltingJob existing = smeltingJobs.get(signature);
		if (existing == null && activeJobCount() >= MAX_ACTIVE_JOBS) {
			player.sendSystemMessage(error("The AI is busy inventing other recipes. Try again shortly."));
			return false;
		}
		SmeltingRecipeSignature lockSignature = existing == null ? signature : existing.signature;
		furnaceLocks.put(furnace, lockSignature);
		if (existing != null) {
			existing.furnaces.add(furnace);
			existing.requesters.add(player.getUUID());
			return true;
		}

		ActiveSmeltingJob job = new ActiveSmeltingJob(signature);
		job.furnaces.add(furnace);
		job.requesters.add(player.getUUID());
		smeltingJobs.put(signature, job);
		try {
			job.task = submitGeneration(job.promise, signature.toAiContext());
		} catch (Throwable submissionFailure) {
			smeltingJobs.remove(signature);
			unlockFurnace(furnace, signature);
			player.sendSystemMessage(error("Could not start the smelting recipe generation job."));
			LittleChemistry.LOGGER.error("Could not submit a smelting recipe generation job", submissionFailure);
			return false;
		}

		job.promise.whenComplete((generated, failure) -> {
			if (!server.isRunning()) return;
			server.execute(() -> completeSmelting(job, generated, failure));
		});
		return true;
	}

	public void tableContentsChanged(SharedCraftingContainer table) {
		if (!table.isPortable()) tablesDirty = true;
		tableViewStateChanged(table);
	}

	void tableViewerClosed(SharedCraftingContainer table) {
		if (table.isPortable()) {
			for (ActiveJob job : jobs.values()) job.tables.remove(table);
			RecipeSignature lockSignature = table.lockSignature();
			if (lockSignature != null) table.unlock(lockSignature);
			return;
		}
		if (!table.hasViewers() && !table.isLocked() && table.isEmpty() && tables.remove(table.key(), table)) {
			tablesDirty = true;
		}
	}

	public void tableViewStateChanged(SharedCraftingContainer table) {
		for (CraftingMenu menu : table.viewers()) {
			menu.slotsChanged(table);
			menu.broadcastChanges();
		}
	}

	public void tableRemoved(ServerLevel level, BlockPos pos) {
		TableKey key = TableKey.of(level, pos);
		SharedCraftingContainer table = tables.remove(key);
		if (table == null) return;
		for (ActiveJob job : jobs.values()) job.tables.remove(table);
		NonNullList<ItemStack> contents = table.drain();
		tablesDirty = true;
		Containers.dropContents(level, pos, contents);
	}

	public void removeRecipesFor(Set<String> outputNames) throws IOException {
		cancelJobsReferencing(outputNames);
		Map<RecipeSignature, AiCraftingRecipe> updated = new LinkedHashMap<>(recipes);
		updated.entrySet().removeIf(entry -> outputNames.contains(entry.getValue().outputName())
				|| entry.getKey().referencesDynamicContent(outputNames));
		Map<SmeltingRecipeSignature, AiSmeltingRecipe> updatedSmelting = new LinkedHashMap<>(smeltingRecipes);
		updatedSmelting.entrySet().removeIf(entry -> outputNames.contains(entry.getValue().outputName())
				|| entry.getKey().referencesDynamicContent(outputNames));
		if (updated.size() == recipes.size() && updatedSmelting.size() == smeltingRecipes.size()) return;
		saveRecipes(updated, updatedSmelting);
		recipes.clear();
		recipes.putAll(updated);
		smeltingRecipes.clear();
		smeltingRecipes.putAll(updatedSmelting);
		rebuildRecipeIndex();
	}

	private void cancelJobsReferencing(Set<String> deletedNames) {
		for (ActiveJob job : List.copyOf(jobs.values())) {
			if (!job.signature.referencesDynamicContent(deletedNames) || !jobs.remove(job.signature, job)) continue;
			if (job.task != null) job.task.cancel(true);
			notifyRequesters(job, error("Recipe generation stopped because one of its ingredients was deleted."));
			job.tables.forEach(table -> table.unlock(job.signature));
		}
		for (ActiveSmeltingJob job : List.copyOf(smeltingJobs.values())) {
			if (!job.signature.referencesDynamicContent(deletedNames)
					|| !smeltingJobs.remove(job.signature, job)) continue;
			if (job.task != null) job.task.cancel(true);
			notifyRequesters(job.requesters,
					error("Smelting recipe generation stopped because its ingredient was deleted."));
			job.furnaces.forEach(furnace -> unlockFurnace(furnace, job.signature));
		}
	}

	private void complete(ActiveJob job, RecipeGenerationAgent.GeneratedRecipe generated, Throwable failure) {
		if (jobs.get(job.signature) != job) return;
		DynamicContentManager contentManager = null;
		DynamicContentDefinition createdDefinition = null;
		try {
			if (failure != null) {
				String message = safeMessage(failure);
				notifyRequesters(job, error("The AI could not make this recipe: " + message));
				LittleChemistry.LOGGER.warn("AI recipe generation failed: {}", message);
				return;
			}
			contentManager = DynamicContentManager.active();
			if (contentManager == null || !contentManager.belongsTo(server)) {
				notifyRequesters(job, error("The server stopped before the recipe could be added."));
				return;
			}
			String displayName = uniqueDisplayName(contentManager, generated.displayName());
			createdDefinition = generated.armorSlot() == null
					? contentManager.createGenerated(generated.type(), displayName, generated.content())
					: contentManager.createGenerated(generated.type(), generated.armorSlot(), displayName, generated.content());
			installRecipe(job.signature, createdDefinition.name(), generated.outputCount());
			String quantity = generated.outputCount() == 1 ? "" : generated.outputCount() + " × ";
			notifyRequesters(job, Component.literal("[Little Chemistry] Recipe invented: " + quantity)
					.withStyle(ChatFormatting.GREEN)
					.append(DynamicContentObjects.displayName(createdDefinition))
					.append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
		} catch (Exception error) {
			if (createdDefinition != null && contentManager != null) {
				try {
					contentManager.delete(List.of(createdDefinition.name()));
				} catch (Exception rollbackFailure) {
					error.addSuppressed(rollbackFailure);
					LittleChemistry.LOGGER.error("Could not roll back dynamic content after recipe persistence failed", rollbackFailure);
				}
			}
			String message = safeMessage(error);
			notifyRequesters(job, AiCraftingManager.error("Could not save the invented recipe: " + message));
			LittleChemistry.LOGGER.error("Could not commit an AI crafting recipe", error);
		} finally {
			jobs.remove(job.signature, job);
			job.tables.forEach(table -> table.unlock(job.signature));
		}
	}

	private void installRecipe(RecipeSignature signature, String outputName, int outputCount) throws IOException {
		Map<RecipeSignature, AiCraftingRecipe> updated = new LinkedHashMap<>(recipes);
		updated.put(signature, new AiCraftingRecipe(signature, outputName, outputCount));
		saveRecipes(updated, smeltingRecipes);
		recipes.clear();
		recipes.putAll(updated);
		rebuildRecipeIndex();
	}

	private void completeSmelting(ActiveSmeltingJob job, RecipeGenerationAgent.GeneratedRecipe generated,
			Throwable failure) {
		if (smeltingJobs.get(job.signature) != job) return;
		DynamicContentManager contentManager = null;
		DynamicContentDefinition createdDefinition = null;
		try {
			if (failure != null) {
				String message = safeMessage(failure);
				notifyRequesters(job.requesters, error("The AI could not make this smelting recipe: " + message));
				LittleChemistry.LOGGER.warn("AI smelting recipe generation failed: {}", message);
				return;
			}
			if (job.signature.referencesUnavailableDynamicContent()) {
				notifyRequesters(job.requesters, error("The smelting ingredient was deleted while its recipe was being made."));
				return;
			}
			if (server.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
					new SingleRecipeInput(job.signature.ingredient()), server.overworld()).isPresent()) {
				notifyRequesters(job.requesters, error("That ingredient gained a smelting recipe while the AI was working."));
				return;
			}
			contentManager = DynamicContentManager.active();
			if (contentManager == null || !contentManager.belongsTo(server)) {
				notifyRequesters(job.requesters, error("The server stopped before the smelting recipe could be added."));
				return;
			}
			String displayName = uniqueDisplayName(contentManager, generated.displayName());
			createdDefinition = generated.armorSlot() == null
					? contentManager.createGenerated(generated.type(), displayName, generated.content())
					: contentManager.createGenerated(generated.type(), generated.armorSlot(), displayName, generated.content());
			installSmeltingRecipe(job.signature, createdDefinition.name(), generated.outputCount());
			String quantity = generated.outputCount() == 1 ? "" : generated.outputCount() + " × ";
			notifyRequesters(job.requesters, Component.literal("[Little Chemistry] Smelting recipe invented: " + quantity)
					.withStyle(ChatFormatting.GREEN)
					.append(DynamicContentObjects.displayName(createdDefinition))
					.append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
		} catch (Exception error) {
			if (createdDefinition != null && contentManager != null) {
				try {
					contentManager.delete(List.of(createdDefinition.name()));
				} catch (Exception rollbackFailure) {
					error.addSuppressed(rollbackFailure);
					LittleChemistry.LOGGER.error("Could not roll back dynamic content after smelting recipe persistence failed",
							rollbackFailure);
				}
			}
			String message = safeMessage(error);
			notifyRequesters(job.requesters, AiCraftingManager.error(
					"Could not save the invented smelting recipe: " + message));
			LittleChemistry.LOGGER.error("Could not commit an AI smelting recipe", error);
		} finally {
			smeltingJobs.remove(job.signature, job);
			job.furnaces.forEach(furnace -> unlockFurnace(furnace, job.signature));
		}
	}

	private void installSmeltingRecipe(SmeltingRecipeSignature signature, String outputName, int outputCount)
			throws IOException {
		Map<SmeltingRecipeSignature, AiSmeltingRecipe> updated = new LinkedHashMap<>(smeltingRecipes);
		AiSmeltingRecipe previous = updated.get(signature);
		ResourceKey<Recipe<?>> recipeKey = previous == null ? newSmeltingRecipeKey() : previous.recipeKey();
		updated.put(signature, new AiSmeltingRecipe(recipeKey, signature, outputName, outputCount,
				AiSmeltingRecipe.DEFAULT_EXPERIENCE, AiSmeltingRecipe.DEFAULT_COOKING_TIME));
		saveRecipes(recipes, updated);
		smeltingRecipes.clear();
		smeltingRecipes.putAll(updated);
		rebuildRecipeIndex();
	}

	private static String uniqueDisplayName(DynamicContentManager manager, String requested) {
		String base = DynamicContentManager.normalizeDisplayName(requested);
		if (!manager.containsName(base)) return base;
		for (int suffix = 2; suffix < 10_000; suffix++) {
			String ending = " " + suffix;
			String trimmed = base.substring(0, Math.min(base.length(), 64 - ending.length())).stripTrailing();
			String candidate = trimmed + ending;
			if (!manager.containsName(candidate)) return candidate;
		}
		throw new IllegalArgumentException("Could not choose a unique generated content name");
	}

	private void notifyRequesters(ActiveJob job, Component message) {
		notifyRequesters(job.requesters, message);
	}

	private void notifyRequesters(Set<UUID> requesters, Component message) {
		for (UUID playerId : requesters) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) player.sendSystemMessage(message);
		}
	}

	private Future<?> submitGeneration(CompletableFuture<RecipeGenerationAgent.GeneratedRecipe> promise,
			JsonObject context) {
		return GENERATION_EXECUTOR.submit(() -> {
			try {
				OpenAiClient client = new OpenAiClient(new AuthConfig(), GenerationModel.SOL.modelId(), REASONING_EFFORT);
				promise.complete(new RecipeGenerationAgent(client).generate(context));
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				promise.completeExceptionally(interrupted);
			} catch (Throwable failure) {
				promise.completeExceptionally(failure);
			}
		});
	}

	private int activeJobCount() {
		return jobs.size() + smeltingJobs.size();
	}

	private void unlockFurnace(Container furnace, SmeltingRecipeSignature signature) {
		furnaceLocks.remove(furnace, signature);
	}

	private RecipeHolder<CraftingRecipe> craftingHolder(AiCraftingRecipe recipe) {
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
				LittleChemistry.id("ai/" + Integer.toUnsignedString(recipe.signature().hashCode(), 16)
						+ "_" + recipe.outputName()));
		return new RecipeHolder<>(key, recipe);
	}

	private RecipeHolder<SmeltingRecipe> smeltingHolder(AiSmeltingRecipe recipe) {
		return new RecipeHolder<>(recipe.recipeKey(), recipe);
	}

	private ResourceKey<Recipe<?>> newSmeltingRecipeKey() {
		ResourceKey<Recipe<?>> key;
		do {
			key = ResourceKey.create(Registries.RECIPE,
					LittleChemistry.id("ai/smelting/" + UUID.randomUUID()));
		} while (hasSmeltingRecipeKey(key));
		return key;
	}

	private boolean hasSmeltingRecipeKey(ResourceKey<Recipe<?>> key) {
		return smeltingRecipes.values().stream().anyMatch(recipe -> recipe.recipeKey().equals(key));
	}

	private ResourceKey<Recipe<?>> legacySmeltingRecipeKey(SmeltingRecipeSignature signature, String outputName) {
		return ResourceKey.create(Registries.RECIPE,
				LittleChemistry.id("ai/smelting_" + Integer.toUnsignedString(signature.hashCode(), 16)
						+ "_" + outputName));
	}

	private void rebuildRecipeIndex() {
		recipesByKey.clear();
		for (AiCraftingRecipe recipe : recipes.values()) {
			RecipeHolder<CraftingRecipe> holder = craftingHolder(recipe);
			recipesByKey.put(holder.id(), holder);
		}
		for (AiSmeltingRecipe recipe : smeltingRecipes.values()) {
			RecipeHolder<SmeltingRecipe> holder = smeltingHolder(recipe);
			recipesByKey.put(holder.id(), holder);
		}
	}

	private boolean pruneUnavailableRecipes() {
		boolean craftingRemoved = recipes.entrySet().removeIf(entry ->
				DynamicContentCatalog.find(entry.getValue().outputName()) == null
						|| entry.getKey().referencesUnavailableDynamicContent());
		boolean smeltingRemoved = smeltingRecipes.entrySet().removeIf(entry ->
				DynamicContentCatalog.find(entry.getValue().outputName()) == null
						|| entry.getKey().referencesUnavailableDynamicContent());
		return craftingRemoved || smeltingRemoved;
	}

	private void loadTables() throws IOException {
		if (!Files.isRegularFile(tablesFile)) return;
		JsonObject root = parseObject(tablesFile);
		requireFormat(root, TABLES_FORMAT, TABLES_FORMAT);
		JsonArray encodedTables = root.getAsJsonArray("tables");
		if (encodedTables == null || encodedTables.size() > 100_000) throw new IOException("Invalid shared crafting table list");
		var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (JsonElement element : encodedTables) {
			if (!(element instanceof JsonObject encoded)) throw new IOException("Invalid shared crafting table entry");
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
					Identifier.parse(encoded.get("dimension").getAsString()));
			TableKey key = new TableKey(dimension, encoded.get("pos").getAsLong());
			JsonArray encodedItems = encoded.getAsJsonArray("items");
			if (encodedItems == null || encodedItems.size() != 9) throw new IOException("Invalid shared crafting table inventory");
			List<ItemStack> items = new ArrayList<>(9);
			for (JsonElement encodedItem : encodedItems) {
				items.add(ItemStack.OPTIONAL_CODEC.parse(ops, encodedItem).getOrThrow(IOException::new));
			}
			tables.put(key, SharedCraftingContainer.physical(this, key, items));
		}
	}

	private void loadRecipes() throws IOException {
		if (!Files.isRegularFile(recipesFile)) return;
		JsonObject root = parseObject(recipesFile);
		int format = requireFormat(root, 1, RECIPES_FORMAT);
		JsonArray encodedRecipes = root.getAsJsonArray("recipes");
		if (encodedRecipes == null || encodedRecipes.size() > 100_000) throw new IOException("Invalid AI recipe list");
		var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (JsonElement element : encodedRecipes) {
			if (!(element instanceof JsonObject encoded)) throw new IOException("Invalid AI recipe entry");
			String output = encoded.get("output").getAsString();
			if (!output.matches("[a-z0-9_]{1,64}")) throw new IOException("Invalid AI recipe output identifier");
			double rawOutputCount = format >= 2 && encoded.has("outputCount")
					? encoded.get("outputCount").getAsDouble() : 1.0;
			if (!Double.isFinite(rawOutputCount) || rawOutputCount != Math.rint(rawOutputCount)
					|| rawOutputCount < 1 || rawOutputCount > 64) {
				throw new IOException("Invalid AI recipe output count");
			}
			int outputCount = (int) rawOutputCount;
			String type = format >= 3 ? encoded.get("type").getAsString() : "crafting";
			switch (type) {
				case "crafting" -> {
					int width = encoded.get("width").getAsInt();
					int height = encoded.get("height").getAsInt();
					JsonArray encodedIngredients = encoded.getAsJsonArray("ingredients");
					if (encodedIngredients == null || encodedIngredients.size() != width * height) {
						throw new IOException("Invalid AI crafting recipe ingredients");
					}
					List<ItemStack> ingredients = new ArrayList<>(encodedIngredients.size());
					for (JsonElement ingredient : encodedIngredients) {
						ingredients.add(ItemStack.OPTIONAL_CODEC.parse(ops, ingredient).getOrThrow(IOException::new));
					}
					RecipeSignature signature = new RecipeSignature(width, height, ingredients);
					recipes.put(signature, new AiCraftingRecipe(signature, output, outputCount));
				}
				case "smelting" -> {
					JsonElement encodedIngredient = encoded.get("ingredient");
					if (encodedIngredient == null) throw new IOException("Invalid AI smelting recipe ingredient");
					ItemStack ingredient = ItemStack.OPTIONAL_CODEC.parse(ops, encodedIngredient)
							.getOrThrow(IOException::new);
					if (ingredient.isEmpty()) throw new IOException("Invalid empty AI smelting recipe ingredient");
					SmeltingRecipeSignature signature = new SmeltingRecipeSignature(ingredient);
					ResourceKey<Recipe<?>> recipeKey = readSmeltingRecipeKey(encoded, signature, output);
					if (!encoded.has("experience") || !encoded.has("cookingTime")) recipesNeedRewrite = true;
					float experience = encoded.has("experience") ? encoded.get("experience").getAsFloat()
							: AiSmeltingRecipe.DEFAULT_EXPERIENCE;
					int cookingTime = encoded.has("cookingTime") ? encoded.get("cookingTime").getAsInt()
							: AiSmeltingRecipe.DEFAULT_COOKING_TIME;
					if (!Float.isFinite(experience) || experience < 0.0F) {
						throw new IOException("Invalid AI smelting recipe experience");
					}
					if (cookingTime < 1 || cookingTime > Short.MAX_VALUE) {
						throw new IOException("Invalid AI smelting recipe cooking time");
					}
					smeltingRecipes.put(signature, new AiSmeltingRecipe(
							recipeKey, signature, output, outputCount, experience, cookingTime));
				}
				default -> throw new IOException("Unknown AI recipe type: " + type);
			}
		}
	}

	private ResourceKey<Recipe<?>> readSmeltingRecipeKey(JsonObject encoded, SmeltingRecipeSignature signature,
			String outputName) throws IOException {
		if (!encoded.has("id")) {
			recipesNeedRewrite = true;
			return legacySmeltingRecipeKey(signature, outputName);
		}
		try {
			Identifier id = Identifier.parse(encoded.get("id").getAsString());
			if (!LittleChemistry.MOD_ID.equals(id.getNamespace())
					|| !(id.getPath().startsWith("ai/smelting/") || id.getPath().startsWith("ai/smelting_"))) {
				throw new IOException("Invalid AI smelting recipe identifier");
			}
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
			if (smeltingRecipes.values().stream().anyMatch(recipe -> recipe.recipeKey().equals(key))) {
				throw new IOException("Duplicate AI smelting recipe identifier");
			}
			return key;
		} catch (IllegalArgumentException | NullPointerException error) {
			throw new IOException("Invalid AI smelting recipe identifier", error);
		}
	}

	private void flushTables() throws IOException {
		if (!tablesDirty) return;
		JsonObject root = new JsonObject();
		root.addProperty("format", TABLES_FORMAT);
		JsonArray encodedTables = new JsonArray();
		var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (SharedCraftingContainer table : tables.values()) {
			if (table.isEmpty()) continue;
			JsonObject encoded = new JsonObject();
			encoded.addProperty("dimension", table.key().dimension().identifier().toString());
			encoded.addProperty("pos", table.key().packedPos());
			JsonArray items = new JsonArray();
			for (ItemStack stack : table.copyItems()) {
				items.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).getOrThrow(IOException::new));
			}
			encoded.add("items", items);
			encodedTables.add(encoded);
		}
		root.add("tables", encodedTables);
		writeAtomically(tablesFile, root);
		tablesDirty = false;
	}

	private void saveRecipes(Map<RecipeSignature, AiCraftingRecipe> savedRecipes,
			Map<SmeltingRecipeSignature, AiSmeltingRecipe> savedSmeltingRecipes) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("format", RECIPES_FORMAT);
		JsonArray encodedRecipes = new JsonArray();
		var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (AiCraftingRecipe recipe : savedRecipes.values()) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("type", "crafting");
			encoded.addProperty("width", recipe.signature().width());
			encoded.addProperty("height", recipe.signature().height());
			JsonArray ingredients = new JsonArray();
			for (ItemStack ingredient : recipe.signature().ingredients()) {
				ingredients.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, ingredient).getOrThrow(IOException::new));
			}
			encoded.add("ingredients", ingredients);
			encoded.addProperty("output", recipe.outputName());
			encoded.addProperty("outputCount", recipe.outputCount());
			encodedRecipes.add(encoded);
		}
		for (AiSmeltingRecipe recipe : savedSmeltingRecipes.values()) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("type", "smelting");
			encoded.addProperty("id", recipe.recipeKey().identifier().toString());
			encoded.add("ingredient", ItemStack.OPTIONAL_CODEC.encodeStart(ops, recipe.signature().ingredient())
					.getOrThrow(IOException::new));
			encoded.addProperty("output", recipe.outputName());
			encoded.addProperty("outputCount", recipe.outputCount());
			encoded.addProperty("experience", recipe.experience());
			encoded.addProperty("cookingTime", recipe.cookingTime());
			encodedRecipes.add(encoded);
		}
		root.add("recipes", encodedRecipes);
		writeAtomically(recipesFile, root);
	}

	private static JsonObject parseObject(Path path) throws IOException {
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path));
			if (parsed instanceof JsonObject object) return object;
			throw new IOException("Expected a JSON object in " + path.getFileName());
		} catch (RuntimeException error) {
			throw new IOException("Could not parse " + path.getFileName(), error);
		}
	}

	private static int requireFormat(JsonObject root, int minimum, int maximum) throws IOException {
		if (!root.has("format")) {
			throw new IOException("Unsupported Little Chemistry crafting data format");
		}
		int format = root.get("format").getAsInt();
		if (format < minimum || format > maximum) {
			throw new IOException("Unsupported Little Chemistry crafting data format");
		}
		return format;
	}

	private static void writeAtomically(Path destination, JsonObject root) throws IOException {
		Files.createDirectories(destination.getParent());
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.writeString(temporary, root.toString());
		try {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static Component error(String message) {
		return Component.literal("[Little Chemistry] " + message).withStyle(ChatFormatting.RED);
	}

	private static String safeMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		String message = current.getMessage();
		if (message == null || message.isBlank()) return current.getClass().getSimpleName();
		String safe = message.replaceAll("[\\r\\n]+", " ").trim();
		return safe.length() <= 500 ? safe : safe.substring(0, 500) + "…";
	}

	public record TableKey(ResourceKey<Level> dimension, long packedPos) {
		static TableKey of(ServerLevel level, BlockPos pos) {
			return new TableKey(level.dimension(), pos.asLong());
		}
	}

	private final class ActiveJob {
		private final RecipeSignature signature;
		private final Set<SharedCraftingContainer> tables = new HashSet<>();
		private final Set<UUID> requesters = new HashSet<>();
		private final CompletableFuture<RecipeGenerationAgent.GeneratedRecipe> promise = new CompletableFuture<>();
		private Future<?> task;

		private ActiveJob(RecipeSignature signature) {
			this.signature = signature;
		}
	}

	private final class ActiveSmeltingJob {
		private final SmeltingRecipeSignature signature;
		private final Set<Container> furnaces = Collections.newSetFromMap(new IdentityHashMap<>());
		private final Set<UUID> requesters = new HashSet<>();
		private final CompletableFuture<RecipeGenerationAgent.GeneratedRecipe> promise = new CompletableFuture<>();
		private Future<?> task;

		private ActiveSmeltingJob(SmeltingRecipeSignature signature) {
			this.signature = signature;
		}
	}
}
