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
import com.yeyito.littlechemistry.ai.generation.GenerationWorkspace;
import com.yeyito.littlechemistry.ai.generation.ExocortexConversationExporter;
import com.yeyito.littlechemistry.ai.generation.RecipeGenerationAgent;
import com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.world.level.block.Blocks;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Owns physical and portable crafting grids, persistent AI recipes, and active recipe jobs. */
public final class AiCraftingManager {
	private static final int TABLES_FORMAT = 2;
	private static final int RECIPES_FORMAT = 5;
	// This is the canonical server-wide limit for concurrent crafts of every supported kind.
	private static final int MAX_ACTIVE_JOBS = 32;
	private static final int PARTICLE_INTERVAL_TICKS = 8;
	private static final String REASONING_EFFORT = "medium";
	private static final ExecutorService GENERATION_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
	private static volatile AiCraftingManager active;

	private final MinecraftServer server;
	private final Path tablesFile;
	private final Path recipesFile;
	private final Path recipeTransactionsDirectory;
	private final Map<TableKey, SharedCraftingContainer> tables = new HashMap<>();
	private final Map<UUID, SharedCraftingContainer> portableTables = new HashMap<>();
	private final Map<RecipeSignature, AiCraftingRecipe> recipes = new LinkedHashMap<>();
	private final Map<SmeltingRecipeSignature, AiSmeltingRecipe> smeltingRecipes = new LinkedHashMap<>();
	private final Map<WorkstationRecipeSignature, AiWorkstationRecipe> workstationRecipes = new LinkedHashMap<>();
	private final Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipesByKey = new HashMap<>();
	private final Map<RecipeSignature, RecipeDisplayId> recipeDisplayIds = new LinkedHashMap<>();
	private final Map<RecipeDisplayId, AiCraftingRecipe> recipesByDisplayId = new HashMap<>();
	private final Map<RecipeSignature, ActiveJob> jobs = new HashMap<>();
	private final Map<SmeltingRecipeSignature, ActiveSmeltingJob> smeltingJobs = new HashMap<>();
	/** Position-keyed lock registry; equal signatures at different positions may share one job value. */
	private final Map<WorkstationKey, ActiveWorkstationJob> workstationJobs = new HashMap<>();
	private final Map<Container, SmeltingRecipeSignature> furnaceLocks = new IdentityHashMap<>();
	private final Set<SharedCraftingContainer> animatedTables =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private int nextRecipeDisplayId = -1;
	private int particleTicks;
	private boolean tablesDirty;
	private boolean recipesNeedRewrite;

	private AiCraftingManager(MinecraftServer server) throws IOException {
		this.server = server;
		Path directory = server.getWorldPath(LevelResource.ROOT).resolve("little-chemistry");
		this.tablesFile = directory.resolve("crafting-tables.json");
		this.recipesFile = directory.resolve("ai-recipes.json");
		this.recipeTransactionsDirectory = directory.resolve("recipe-transactions");
		loadTables();
		loadRecipes();
		if (pruneUnavailableRecipes()) recipesNeedRewrite = true;
		if (recipesNeedRewrite) saveRecipes(recipes, smeltingRecipes);
		recoverRecipeTransactions();
		rebuildRecipeIndex();
		for (Map.Entry<RecipeSignature, AiCraftingRecipe> entry : recipes.entrySet()) {
			assignRecipeDisplay(entry.getKey(), entry.getValue());
		}
		clearInvalidReadyTables();
	}

	public static void start(MinecraftServer server) {
		try {
			AiCraftingManager manager = new AiCraftingManager(server);
			active = manager;
			LittleChemistry.LOGGER.info("Loaded {} shared crafting tables, {} AI crafting recipes, {} AI smelting recipes, and {} AI workstation recipes",
					manager.tables.size(), manager.recipes.size(), manager.smeltingRecipes.size(),
					manager.workstationRecipes.size());
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
		manager.portableTables.clear();
		for (ActiveSmeltingJob job : List.copyOf(manager.smeltingJobs.values())) {
			job.promise.completeExceptionally(new IllegalStateException("Server stopped during recipe generation"));
			if (job.task != null) job.task.cancel(true);
		}
		manager.smeltingJobs.clear();
		Set<ActiveWorkstationJob> workstationJobs = new HashSet<>(manager.workstationJobs.values());
		for (ActiveWorkstationJob job : workstationJobs) {
			job.promise.completeExceptionally(new IllegalStateException("Server stopped during recipe generation"));
			if (job.task != null) job.task.cancel(true);
		}
		manager.workstationJobs.clear();
		for (ActiveWorkstationJob job : workstationJobs) {
			job.workstations.forEach(key -> manager.finishLoadedWorkstation(key, job.signature, false));
		}
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
		if (manager == null || manager.server != server) return;
		if (++manager.particleTicks >= PARTICLE_INTERVAL_TICKS) {
			manager.particleTicks = 0;
			manager.emitTableParticles();
		}
		if (!manager.tablesDirty) return;
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

	public SharedCraftingContainer portableTable(ServerLevel level, UUID tableId, ItemStack carrier) {
		SharedCraftingContainer table = portableTables.get(tableId);
		if (table == null) {
			NonNullList<ItemStack> items = portableItems(carrier);
			PortableCraftingState savedState = carrier.get(PortableCraftingComponents.STATE);
			boolean recipeReady = savedState != null && hasCraftingResult(items, level);
			table = SharedCraftingContainer.portable(this, tableId, items, recipeReady);
			portableTables.put(tableId, table);
		}
		table.attachPortableCarrier(carrier);
		return table;
	}

	/** Reattaches moved/reloaded carrier stacks and repairs stale state left by an interrupted server. */
	public void reconcilePortableStack(ItemStack carrier, ServerLevel level) {
		UUID tableId = carrier.get(PortableCraftingComponents.TABLE_ID);
		if (tableId == null) return;

		SharedCraftingContainer table = portableTables.get(tableId);
		if (table != null) {
			table.attachPortableCarrier(carrier);
			if (!table.hasViewers() && !table.isLocked()) portableTables.remove(tableId, table);
			return;
		}

		PortableCraftingState savedState = carrier.get(PortableCraftingComponents.STATE);
		if (savedState == null) return;
		boolean recipeReady = hasCraftingResult(portableItems(carrier), level);
		if (recipeReady) {
			if (savedState != PortableCraftingState.READY) {
				carrier.set(PortableCraftingComponents.STATE, PortableCraftingState.READY);
			}
		} else {
			carrier.remove(PortableCraftingComponents.STATE);
		}
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

	public AiWorkstationRecipe findWorkstationRecipe(WorkstationRecipeSignature signature) {
		if (signature == null) return null;
		AiWorkstationRecipe recipe = workstationRecipes.get(signature);
		return recipe != null && recipe.outputAvailable() ? recipe : null;
	}

	/** Returns the recipe signature currently locking this placed workstation. */
	public Optional<WorkstationRecipeSignature> workstationLockSignature(ServerLevel level, BlockPos pos) {
		ActiveWorkstationJob job = workstationJob(level, pos);
		return job == null ? Optional.empty() : Optional.of(job.signature);
	}

	/** Returns the stable workstation slot IDs locked while this position's recipe is generated. */
	public Set<String> workstationLockedSlotIds(ServerLevel level, BlockPos pos) {
		ActiveWorkstationJob job = workstationJob(level, pos);
		return job == null ? Set.of() : job.lockedSlotIds;
	}

	/** Resolves locked slot IDs against the workstation's current data-defined slot order. */
	public Set<Integer> workstationLockedSlotIndexes(ServerLevel level, BlockPos pos) {
		ActiveWorkstationJob job = workstationJob(level, pos);
		if (job == null) return Set.of();
		DynamicContentDefinition definition = DynamicContentCatalog.find(job.signature.workstationName());
		if (definition == null || definition.workstation() == null) return Set.of();
		Set<Integer> result = new HashSet<>();
		for (int index = 0; index < definition.workstation().slots().size(); index++) {
			if (job.lockedSlotIds.contains(definition.workstation().slots().get(index).id())) result.add(index);
		}
		return Set.copyOf(result);
	}

	public boolean isWorkstationSlotLocked(ServerLevel level, BlockPos pos, int slotIndex) {
		ActiveWorkstationJob job = workstationJob(level, pos);
		if (job == null) return false;
		DynamicContentDefinition definition = DynamicContentCatalog.find(job.signature.workstationName());
		return definition != null && definition.workstation() != null && slotIndex >= 0
				&& slotIndex < definition.workstation().slots().size()
				&& job.lockedSlotIds.contains(definition.workstation().slots().get(slotIndex).id());
	}

	public boolean isWorkstationLocked(ServerLevel level, BlockPos pos) {
		return workstationJob(level, pos) != null;
	}

	public Optional<RecipeHolder<?>> findRecipeByKey(ResourceKey<Recipe<?>> key) {
		return Optional.ofNullable(recipesByKey.get(key));
	}

	public boolean isSmeltingLocked(Container furnace) {
		return furnaceLocks.containsKey(furnace);
	}

	/** Adds every server-owned runtime recipe to a player's otherwise vanilla recipe book. */
	public void sendRecipeBookSnapshot(ServerPlayer player) {
		List<ClientboundRecipeBookAddPacket.Entry> entries = new ArrayList<>(recipes.size());
		for (Map.Entry<RecipeSignature, AiCraftingRecipe> entry : recipes.entrySet()) {
			ClientboundRecipeBookAddPacket.Entry bookEntry = recipeBookEntry(
					recipeDisplayIds.get(entry.getKey()), entry.getValue());
			if (bookEntry != null) entries.add(bookEntry);
		}
		if (!entries.isEmpty()) {
			player.connection.send(new ClientboundRecipeBookAddPacket(entries, false));
		}
	}

	/**
	 * Handles a click on one of the negative display IDs reserved for runtime
	 * recipes. Vanilla placement only understands recipes loaded by RecipeManager,
	 * so runtime recipes need an exact-component-aware placement path of their own.
	 */
	public boolean handleRecipeBookPlacement(ServerPlayer player, int containerId,
			RecipeDisplayId displayId, boolean useMaxItems) {
		AiCraftingRecipe recipe = recipesByDisplayId.get(displayId);
		if (recipe == null) return false;
		if (player.isSpectator() || player.isDeadOrDying()
				|| player.containerMenu.containerId != containerId
				|| !player.containerMenu.stillValid(player)
				|| !(player.containerMenu instanceof AbstractCraftingMenu menu)) {
			return true;
		}
		if (menu instanceof AiCraftingMenuAccess access && access.littleChemistry$getSharedTable() != null
				&& access.littleChemistry$getSharedTable().isLocked()) {
			return true;
		}

		RecipeSignature signature = recipe.signature();
		if (signature.width() > menu.getGridWidth() || signature.height() > menu.getGridHeight()) return true;
		List<Slot> grid = menu.getInputGridSlots();
		Inventory inventory = player.getInventory();
		UUID excludedPortableId = null;
		if (menu instanceof AiCraftingMenuAccess access) {
			SharedCraftingContainer table = access.littleChemistry$getSharedTable();
			if (table != null && table.isPortable()) excludedPortableId = table.portableId();
		}
		if (!player.isCreative() && !canReturnGridToInventory(inventory, grid)) return true;

		int availableCrafts = biggestCraftableStack(signature, inventory, grid, excludedPortableId);
		if (availableCrafts <= 0) {
			clearGridIntoInventory(inventory, grid);
			RecipeDisplay display = recipe.display().stream().findFirst().orElse(null);
			if (display != null) {
				player.connection.send(new ClientboundPlaceGhostRecipePacket(containerId, display));
			}
			return true;
		}

		boolean alreadyMatches = recipe.matches(craftingInput(menu, grid), player.level());
		int amount = useMaxItems ? availableCrafts : 1;
		if (!useMaxItems && alreadyMatches) {
			int smallestPlacedStack = grid.stream()
					.map(Slot::getItem)
					.filter(stack -> !stack.isEmpty())
					.mapToInt(ItemStack::getCount)
					.min()
					.orElse(0);
			amount = Math.min(availableCrafts, smallestPlacedStack + 1);
			if (amount <= smallestPlacedStack) return true;
		}

		clearGridIntoInventory(inventory, grid);
		int placedAmount = amount;
		UUID protectedPortableId = excludedPortableId;
		PlaceRecipeHelper.placeRecipe(menu.getGridWidth(), menu.getGridHeight(), signature.width(), signature.height(),
				signature.ingredients(), (ingredient, gridIndex, gridX, gridY) -> {
					if (!ingredient.isEmpty()) {
						grid.get(gridIndex).set(takeMatching(
								inventory, ingredient, placedAmount, protectedPortableId));
					}
				});
		inventory.setChanged();
		player.containerMenu.broadcastChanges();
		return true;
	}

	public boolean requestRecipe(ServerPlayer player, SharedCraftingContainer table) {
		return requestRecipe(player, table, null);
	}

	/** Starts a normal recipe job with an optional terminal-conversation export used only by explicit test suites. */
	public boolean requestRecipe(ServerPlayer player, SharedCraftingContainer table,
			ExocortexConversationExporter conversationExporter) {
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
			job.task = submitGeneration(job.promise, context, conversationExporter);
		} catch (Throwable submissionFailure) {
			jobs.remove(signature);
			table.unlock(signature);
			player.sendSystemMessage(error("Could not start the recipe generation job."));
			LittleChemistry.LOGGER.error("Could not submit a recipe generation job", submissionFailure);
			return false;
		}

		job.promise.whenComplete((generated, failure) -> {
			if (!server.isRunning()) {
				discardPendingSource(generated);
				return;
			}
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
			if (!server.isRunning()) {
				discardPendingSource(generated);
				return;
			}
			server.execute(() -> completeSmelting(job, generated, failure));
		});
		return true;
	}

	/** Starts or joins a server-wide recipe job for one placed AI-defined workstation. */
	public boolean requestWorkstationRecipe(ServerPlayer player, DynamicBlockEntity workstation) {
		if (workstation == null || !workstation.isValidWorkstation(player)) {
			player.sendSystemMessage(error("That workstation is no longer available."));
			return false;
		}
		ServerLevel level = (ServerLevel) workstation.getLevel();
		if (level.getServer() != server) {
			player.sendSystemMessage(error("That workstation belongs to a different server."));
			return false;
		}
		BlockPos pos = workstation.getBlockPos();
		WorkstationKey workstationKey = WorkstationKey.of(level, pos);
		if (workstationJobs.containsKey(workstationKey)) {
			player.sendSystemMessage(error("This workstation is already waiting for the AI."));
			return false;
		}
		DynamicContentDefinition definition = workstation.workstationDefinition();
		WorkstationRecipeRequest request = workstation.captureWorkstationRecipe(player);
		WorkstationRecipeSignature signature;
		try {
			signature = WorkstationRecipeSignature.capture(definition, request, workstation);
		} catch (IllegalArgumentException invalid) {
			player.sendSystemMessage(error("This workstation rejected its current inputs: " + safeMessage(invalid)));
			return false;
		}
		if (signature == null) {
			player.sendSystemMessage(error("Put a valid set of ingredients in the workstation first."));
			return false;
		}
		if (findWorkstationRecipe(signature) != null) {
			player.sendSystemMessage(error("That workstation input already has a generated recipe."));
			return false;
		}

		ActiveWorkstationJob existing = findWorkstationJob(signature);
		if (existing == null && activeJobCount() >= MAX_ACTIVE_JOBS) {
			player.sendSystemMessage(error("The AI is busy inventing other recipes. Try again shortly."));
			return false;
		}
		ActiveWorkstationJob job = existing == null ? new ActiveWorkstationJob(signature) : existing;
		job.workstations.add(workstationKey);
		workstationJobs.put(workstationKey, job);
		try {
			workstation.lockWorkstation(job.signature);
		} catch (RuntimeException lockFailure) {
			workstationJobs.remove(workstationKey, job);
			job.workstations.remove(workstationKey);
			player.sendSystemMessage(error("Could not lock that workstation for recipe generation."));
			LittleChemistry.LOGGER.error("Could not lock a workstation recipe generation job", lockFailure);
			return false;
		}
		job.requesters.add(player.getUUID());
		if (existing != null) return true;

		try {
			JsonObject context = signature.toAiContext(definition, request.aiContext());
			job.task = submitGeneration(job.promise, context, definition.workstation().recipeSystemPrompt(),
					definition.workstation().recipeDataSchema().schema());
		} catch (Throwable submissionFailure) {
			removeWorkstationJob(job);
			finishLoadedWorkstation(workstationKey, signature, false);
			player.sendSystemMessage(error("Could not start the workstation recipe generation job."));
			LittleChemistry.LOGGER.error("Could not submit a workstation recipe generation job", submissionFailure);
			return false;
		}
		job.promise.whenComplete((generated, failure) -> {
			if (!server.isRunning()) {
				discardPendingSource(generated);
				return;
			}
			server.execute(() -> completeWorkstation(job, generated, failure));
		});
		return true;
	}

	public void tableContentsChanged(SharedCraftingContainer table) {
		if (!table.isPortable()) tablesDirty = true;
		tableViewStateChanged(table);
	}

	void tableGenerationStateChanged(SharedCraftingContainer table) {
		if (table.isPortable()) {
			if (!table.hasViewers() && !table.isLocked()) {
				portableTables.remove(table.portableId(), table);
			}
			return;
		}
		if (table.hasGenerationParticles()) animatedTables.add(table);
		else animatedTables.remove(table);
		tablesDirty = true;
	}

	void tableViewerClosed(SharedCraftingContainer table) {
		if (table.isPortable()) {
			if (!table.hasViewers() && !table.isLocked()) {
				portableTables.remove(table.portableId(), table);
			}
			return;
		}
		if (!table.hasViewers() && !table.isLocked() && table.isEmpty() && tables.remove(table.key(), table)) {
			animatedTables.remove(table);
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
		animatedTables.remove(table);
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
		Map<WorkstationRecipeSignature, AiWorkstationRecipe> updatedWorkstations =
				new LinkedHashMap<>(workstationRecipes);
		updatedWorkstations.entrySet().removeIf(entry -> entry.getValue().referencesOutput(outputNames)
				|| entry.getKey().referencesDynamicContent(outputNames));
		if (updated.size() == recipes.size() && updatedSmelting.size() == smeltingRecipes.size()
				&& updatedWorkstations.size() == workstationRecipes.size()) return;
		List<RecipeDisplayId> removedDisplays = recipes.keySet().stream()
				.filter(signature -> !updated.containsKey(signature))
				.map(recipeDisplayIds::get)
				.filter(java.util.Objects::nonNull)
				.toList();
		saveRecipes(updated, updatedSmelting, updatedWorkstations);
		recipes.clear();
		recipes.putAll(updated);
		smeltingRecipes.clear();
		smeltingRecipes.putAll(updatedSmelting);
		workstationRecipes.clear();
		workstationRecipes.putAll(updatedWorkstations);
		for (RecipeDisplayId displayId : removedDisplays) recipesByDisplayId.remove(displayId);
		recipeDisplayIds.keySet().removeIf(signature -> !updated.containsKey(signature));
		rebuildRecipeIndex();
		clearInvalidReadyTables();
		if (!removedDisplays.isEmpty()) {
			server.getPlayerList().broadcastAll(new ClientboundRecipeBookRemovePacket(removedDisplays));
		}
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
		for (ActiveWorkstationJob job : new HashSet<>(workstationJobs.values())) {
			if (!job.signature.referencesDynamicContent(deletedNames)
					|| !removeWorkstationJob(job)) continue;
			if (job.task != null) job.task.cancel(true);
			notifyRequesters(job.requesters,
					error("Workstation recipe generation stopped because the workstation or an ingredient was deleted."));
			job.workstations.forEach(key -> finishLoadedWorkstation(key, job.signature, false));
		}
	}

	private void complete(ActiveJob job, RecipeGenerationAgent.GeneratedRecipe generated, Throwable failure) {
		if (jobs.get(job.signature) != job) {
			discardPendingSource(generated);
			return;
		}
		DynamicContentManager contentManager = null;
		DynamicContentDefinition createdDefinition = null;
		Path recipeTransaction = null;
		boolean recipeReady = false;
		try {
			if (failure != null) {
				String message = safeMessage(failure);
				notifyRequesters(job, error("The AI could not make this recipe: " + message));
				LittleChemistry.LOGGER.warn("AI recipe generation failed: {}", message);
				return;
			}
			if (job.signature.referencesUnavailableDynamicContent()) {
				notifyRequesters(job, error("A crafting ingredient was deleted while its recipe was being made."));
				return;
			}
			CraftingInput completedInput = CraftingInput.of(
					job.signature.width(), job.signature.height(), job.signature.ingredients());
			if (server.getRecipeManager().getRecipeFor(
					RecipeType.CRAFTING, completedInput, server.overworld()).isPresent()) {
				notifyRequesters(job, error("That crafting grid gained a recipe while the AI was working."));
				return;
			}
			contentManager = DynamicContentManager.active();
			if (contentManager == null || !contentManager.belongsTo(server)) {
				notifyRequesters(job, error("The server stopped before the recipe could be added."));
				return;
			}
			String displayName = uniqueDisplayName(contentManager, generated.displayName());
			recipeTransaction = beginRecipeTransaction(displayName);
			createdDefinition = generated.armorSlot() == null
					? contentManager.createGenerated(generated.type(), displayName, generated.content())
					: contentManager.createGenerated(generated.type(), generated.armorSlot(), displayName, generated.content());
			installRecipe(job.signature, createdDefinition.name(), generated.outputCount());
			finishRecipeTransactionQuietly(recipeTransaction);
			recipeTransaction = null;
			String quantity = generated.outputCount() == 1 ? "" : generated.outputCount() + " × ";
			notifyRequesters(job, Component.literal("[Little Chemistry] Recipe invented: " + quantity)
					.withStyle(ChatFormatting.GREEN)
					.append(DynamicContentObjects.displayName(createdDefinition))
					.append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
			recipeReady = true;
		} catch (Exception error) {
			boolean rolledBack = createdDefinition == null;
			if (createdDefinition != null && contentManager != null) {
				try {
					contentManager.delete(List.of(createdDefinition.name()));
					rolledBack = true;
				} catch (Exception rollbackFailure) {
					error.addSuppressed(rollbackFailure);
					LittleChemistry.LOGGER.error("Could not roll back dynamic content after recipe persistence failed", rollbackFailure);
				}
			}
			if (rolledBack) finishRecipeTransactionQuietly(recipeTransaction);
			String message = safeMessage(error);
			notifyRequesters(job, AiCraftingManager.error("Could not save the invented recipe: " + message));
			LittleChemistry.LOGGER.error("Could not commit an AI crafting recipe", error);
		} finally {
			discardPendingSource(generated);
			jobs.remove(job.signature, job);
			boolean succeeded = recipeReady;
			job.tables.forEach(table -> table.finishGeneration(job.signature, succeeded));
		}
	}

	private void emitTableParticles() {
		Iterator<SharedCraftingContainer> iterator = animatedTables.iterator();
		while (iterator.hasNext()) {
			SharedCraftingContainer table = iterator.next();
			ParticleOptions particle;
			if (table.isLocked()) particle = ParticleTypes.CRIT;
			else if (table.isRecipeReady()) particle = ParticleTypes.HAPPY_VILLAGER;
			else {
				iterator.remove();
				continue;
			}

			ServerLevel level = server.getLevel(table.key().dimension());
			BlockPos pos = BlockPos.of(table.key().packedPos());
			if (level == null || !level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) continue;
			level.sendParticles(particle,
					pos.getX() + 0.5, pos.getY() + 1.08, pos.getZ() + 0.5,
					2, 0.32, 0.08, 0.32, 0.04);
		}
	}

	private void clearInvalidReadyTables() {
		List<SharedCraftingContainer> candidates = new ArrayList<>(animatedTables);
		candidates.addAll(portableTables.values());
		for (SharedCraftingContainer table : candidates) {
			if (!table.isRecipeReady()) continue;
			RecipeSignature signature = RecipeSignature.capture(table);
			AiCraftingRecipe recipe = signature == null ? null : recipes.get(signature);
			if (recipe == null && signature != null) recipe = recipes.get(signature.mirrored());
			if (recipe == null || !recipe.outputAvailable()) table.clearRecipeReady();
		}
	}

	private void installRecipe(RecipeSignature signature, String outputName, int outputCount) throws IOException {
		Map<RecipeSignature, AiCraftingRecipe> updated = new LinkedHashMap<>(recipes);
		AiCraftingRecipe installed = new AiCraftingRecipe(signature, outputName, outputCount);
		updated.put(signature, installed);
		saveRecipes(updated, smeltingRecipes);
		recipes.clear();
		recipes.putAll(updated);
		rebuildRecipeIndex();
		RecipeDisplayId displayId = assignRecipeDisplay(signature, installed);
		ClientboundRecipeBookAddPacket.Entry bookEntry = recipeBookEntry(displayId, installed);
		if (bookEntry != null) {
			server.getPlayerList().broadcastAll(new ClientboundRecipeBookAddPacket(List.of(bookEntry), false));
		}
	}

	private void completeSmelting(ActiveSmeltingJob job, RecipeGenerationAgent.GeneratedRecipe generated,
			Throwable failure) {
		if (smeltingJobs.get(job.signature) != job) {
			discardPendingSource(generated);
			return;
		}
		DynamicContentManager contentManager = null;
		DynamicContentDefinition createdDefinition = null;
		Path recipeTransaction = null;
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
			recipeTransaction = beginRecipeTransaction(displayName);
			createdDefinition = generated.armorSlot() == null
					? contentManager.createGenerated(generated.type(), displayName, generated.content())
					: contentManager.createGenerated(generated.type(), generated.armorSlot(), displayName, generated.content());
			installSmeltingRecipe(job.signature, createdDefinition.name(), generated.outputCount());
			finishRecipeTransactionQuietly(recipeTransaction);
			recipeTransaction = null;
			String quantity = generated.outputCount() == 1 ? "" : generated.outputCount() + " × ";
			notifyRequesters(job.requesters, Component.literal("[Little Chemistry] Smelting recipe invented: " + quantity)
					.withStyle(ChatFormatting.GREEN)
					.append(DynamicContentObjects.displayName(createdDefinition))
					.append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
		} catch (Exception error) {
			boolean rolledBack = createdDefinition == null;
			if (createdDefinition != null && contentManager != null) {
				try {
					contentManager.delete(List.of(createdDefinition.name()));
					rolledBack = true;
				} catch (Exception rollbackFailure) {
					error.addSuppressed(rollbackFailure);
					LittleChemistry.LOGGER.error("Could not roll back dynamic content after smelting recipe persistence failed",
							rollbackFailure);
				}
			}
			if (rolledBack) finishRecipeTransactionQuietly(recipeTransaction);
			String message = safeMessage(error);
			notifyRequesters(job.requesters, AiCraftingManager.error(
					"Could not save the invented smelting recipe: " + message));
			LittleChemistry.LOGGER.error("Could not commit an AI smelting recipe", error);
		} finally {
			discardPendingSource(generated);
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

	private void completeWorkstation(ActiveWorkstationJob job,
			RecipeGenerationAgent.GeneratedRecipe generated, Throwable failure) {
		if (!isWorkstationJobActive(job)) {
			discardPendingSource(generated);
			return;
		}
		DynamicContentManager contentManager = null;
		DynamicContentDefinition createdDefinition = null;
		Path recipeTransaction = null;
		boolean recipeReady = false;
		try {
			if (failure != null) {
				String message = safeMessage(failure);
				notifyRequesters(job.requesters,
						error("The AI could not make this workstation recipe: " + message));
				LittleChemistry.LOGGER.warn("AI workstation recipe generation failed: {}", message);
				return;
			}
			DynamicContentDefinition workstationDefinition = DynamicContentCatalog.find(job.signature.workstationName());
			if (workstationDefinition == null || workstationDefinition.workstation() == null
					|| job.signature.referencesUnavailableDynamicContent()) {
				notifyRequesters(job.requesters,
						error("The workstation or one of its ingredients was deleted while its recipe was being made."));
				return;
			}
			if (generated.isRejected()) {
				installWorkstationRejection(job.signature, generated.rejection());
				notifyRequesters(job.requesters,
						Component.literal("[Little Chemistry] Recipe rejected: ").withStyle(ChatFormatting.RED)
								.append(Component.literal(generated.rejection().description())
										.withStyle(ChatFormatting.GRAY)));
				recipeReady = true;
				return;
			}
			int outputCapacity = workstationDefinition.workstation().slots().stream()
					.filter(slot -> slot.role() == com.yeyito.littlechemistry.content.DynamicWorkstationSlotRole.OUTPUT)
					.findFirst().orElseThrow(() -> new IllegalStateException("Workstation has no primary output slot"))
					.maxStack();
			if (generated.outputCount() > outputCapacity) {
				throw new IllegalArgumentException("Generated workstation output count exceeds the primary output capacity");
			}
			workstationDefinition.workstation().recipeDataSchema().validateValue(generated.recipeData());
			contentManager = DynamicContentManager.active();
			if (contentManager == null || !contentManager.belongsTo(server)) {
				notifyRequesters(job.requesters, error("The server stopped before the workstation recipe could be added."));
				return;
			}
			String displayName = uniqueDisplayName(contentManager, generated.displayName());
			recipeTransaction = beginRecipeTransaction(displayName);
			createdDefinition = generated.armorSlot() == null
					? contentManager.createGenerated(generated.type(), displayName, generated.content())
					: contentManager.createGenerated(generated.type(), generated.armorSlot(), displayName, generated.content());
			installWorkstationRecipe(job.signature, createdDefinition.name(), generated.outputCount(), generated.recipeData());
			finishRecipeTransactionQuietly(recipeTransaction);
			recipeTransaction = null;
			String quantity = generated.outputCount() == 1 ? "" : generated.outputCount() + " × ";
			notifyRequesters(job.requesters,
					Component.literal("[Little Chemistry] Workstation recipe invented: " + quantity)
							.withStyle(ChatFormatting.GREEN)
							.append(DynamicContentObjects.displayName(createdDefinition))
							.append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
			recipeReady = true;
		} catch (Exception error) {
			boolean rolledBack = createdDefinition == null;
			if (createdDefinition != null && contentManager != null) {
				try {
					contentManager.delete(List.of(createdDefinition.name()));
					rolledBack = true;
				} catch (Exception rollbackFailure) {
					error.addSuppressed(rollbackFailure);
					LittleChemistry.LOGGER.error(
							"Could not roll back dynamic content after workstation recipe persistence failed",
							rollbackFailure);
				}
			}
			if (rolledBack) finishRecipeTransactionQuietly(recipeTransaction);
			String message = safeMessage(error);
			notifyRequesters(job.requesters,
					AiCraftingManager.error("Could not save the invented workstation recipe: " + message));
			LittleChemistry.LOGGER.error("Could not commit an AI workstation recipe", error);
		} finally {
			discardPendingSource(generated);
			removeWorkstationJob(job);
			boolean succeeded = recipeReady;
			job.workstations.forEach(key -> finishLoadedWorkstation(key, job.signature, succeeded));
		}
	}

	private void installWorkstationRecipe(WorkstationRecipeSignature signature, String outputName,
			int outputCount, JsonObject recipeData) throws IOException {
		Map<WorkstationRecipeSignature, AiWorkstationRecipe> updated = new LinkedHashMap<>(workstationRecipes);
		updated.put(signature, new AiWorkstationRecipe(signature, outputName, outputCount, recipeData));
		installWorkstationRecipes(updated);
	}

	private void installWorkstationRejection(WorkstationRecipeSignature signature,
			WorkstationRecipeRejection rejection) throws IOException {
		Map<WorkstationRecipeSignature, AiWorkstationRecipe> updated = new LinkedHashMap<>(workstationRecipes);
		updated.put(signature, AiWorkstationRecipe.rejected(signature, rejection));
		installWorkstationRecipes(updated);
	}

	private void installWorkstationRecipes(Map<WorkstationRecipeSignature, AiWorkstationRecipe> updated)
			throws IOException {
		saveRecipes(recipes, smeltingRecipes, updated);
		workstationRecipes.clear();
		workstationRecipes.putAll(updated);
	}

	private static void discardPendingSource(RecipeGenerationAgent.GeneratedRecipe generated) {
		if (generated != null && generated.content() != null) GenerationWorkspace.discardPending(generated.content());
	}

	private Path beginRecipeTransaction(String displayName) throws IOException {
		String identifier = DynamicContentManager.normalizeIdentifier(
				DynamicContentManager.normalizeDisplayName(displayName));
		Files.createDirectories(recipeTransactionsDirectory);
		Path transaction = recipeTransactionsDirectory.resolve(UUID.randomUUID() + ".json");
		JsonObject manifest = new JsonObject();
		manifest.addProperty("contentId", identifier);
		writeAtomically(transaction, manifest);
		return transaction;
	}

	private void finishRecipeTransaction(Path transaction) throws IOException {
		if (transaction == null) return;
		Files.deleteIfExists(transaction);
		try {
			Files.deleteIfExists(recipeTransactionsDirectory);
		} catch (java.nio.file.DirectoryNotEmptyException ignored) {
		}
	}

	private void finishRecipeTransactionQuietly(Path transaction) {
		try {
			finishRecipeTransaction(transaction);
		} catch (IOException cleanupFailure) {
			LittleChemistry.LOGGER.warn("Could not clear a completed AI recipe transaction journal", cleanupFailure);
		}
	}

	private void recoverRecipeTransactions() throws IOException {
		if (!Files.isDirectory(recipeTransactionsDirectory)) return;
		DynamicContentManager contentManager = DynamicContentManager.active();
		try (var paths = Files.list(recipeTransactionsDirectory)) {
			for (Path transaction : paths.filter(Files::isRegularFile).toList()) {
				String contentId;
				try {
					JsonObject manifest = JsonParser.parseString(Files.readString(
							transaction, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
					contentId = manifest.get("contentId").getAsString();
					if (!contentId.matches("[a-z0-9_]{1,64}")) throw new IllegalArgumentException("invalid content ID");
				} catch (RuntimeException malformed) {
					Files.deleteIfExists(transaction);
					continue;
				}
				boolean recipeCommitted = recipes.values().stream().anyMatch(recipe -> recipe.outputName().equals(contentId))
						|| smeltingRecipes.values().stream().anyMatch(recipe -> recipe.outputName().equals(contentId))
						|| workstationRecipes.values().stream().anyMatch(recipe ->
							recipe.outputName() != null && recipe.outputName().equals(contentId));
				if (!recipeCommitted && contentManager != null && contentManager.containsName(contentId)) {
					try {
						contentManager.delete(List.of(contentId));
					} catch (RuntimeException rollbackFailure) {
						throw new IOException("Could not roll back interrupted AI recipe content '" + contentId + "'",
								rollbackFailure);
					}
				}
				Files.deleteIfExists(transaction);
			}
		}
		try {
			Files.deleteIfExists(recipeTransactionsDirectory);
		} catch (java.nio.file.DirectoryNotEmptyException ignored) {
		}
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
		return submitGeneration(promise, context, null, null, null);
	}

	private Future<?> submitGeneration(CompletableFuture<RecipeGenerationAgent.GeneratedRecipe> promise,
			JsonObject context, ExocortexConversationExporter conversationExporter) {
		return submitGeneration(promise, context, null, null, conversationExporter);
	}

	private Future<?> submitGeneration(CompletableFuture<RecipeGenerationAgent.GeneratedRecipe> promise,
			JsonObject context, String workstationPolicy, JsonObject recipeDataSchema) {
		return submitGeneration(promise, context, workstationPolicy, recipeDataSchema, null);
	}

	private Future<?> submitGeneration(CompletableFuture<RecipeGenerationAgent.GeneratedRecipe> promise,
			JsonObject context, String workstationPolicy, JsonObject recipeDataSchema,
			ExocortexConversationExporter conversationExporter) {
		return GENERATION_EXECUTOR.submit(() -> {
			try {
				OpenAiClient client = new OpenAiClient(new AuthConfig(), GenerationModel.SOL.modelId(), REASONING_EFFORT);
				promise.complete(new RecipeGenerationAgent(client).generate(
						context, workstationPolicy, recipeDataSchema, conversationExporter));
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				promise.completeExceptionally(interrupted);
			} catch (Throwable failure) {
				promise.completeExceptionally(failure);
			}
		});
	}

	private int activeJobCount() {
		return jobs.size() + smeltingJobs.size() + new HashSet<>(workstationJobs.values()).size();
	}

	private ActiveWorkstationJob workstationJob(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || level.getServer() != server) return null;
		return workstationJobs.get(WorkstationKey.of(level, pos));
	}

	private ActiveWorkstationJob findWorkstationJob(WorkstationRecipeSignature signature) {
		for (ActiveWorkstationJob job : workstationJobs.values()) {
			if (job.signature.equals(signature)) return job;
		}
		return null;
	}

	private boolean isWorkstationJobActive(ActiveWorkstationJob job) {
		for (WorkstationKey key : job.workstations) {
			if (workstationJobs.get(key) == job) return true;
		}
		return false;
	}

	private boolean removeWorkstationJob(ActiveWorkstationJob job) {
		boolean removed = false;
		for (WorkstationKey key : job.workstations) {
			if (workstationJobs.remove(key, job)) removed = true;
		}
		return removed;
	}

	private void finishLoadedWorkstation(WorkstationKey key, WorkstationRecipeSignature signature,
			boolean succeeded) {
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null) return;
		BlockPos pos = BlockPos.of(key.packedPos());
		if (!level.isLoaded(pos)) return;
		if (level.getBlockEntity(pos) instanceof DynamicBlockEntity workstation) {
			Identifier contentId = workstation.contentId();
			if (contentId != null && LittleChemistry.MOD_ID.equals(contentId.getNamespace())
					&& signature.workstationName().equals(contentId.getPath())) {
				workstation.finishWorkstationGeneration(signature, succeeded);
			}
		}
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
		boolean workstationRemoved = workstationRecipes.entrySet().removeIf(entry -> {
			DynamicContentDefinition workstation = DynamicContentCatalog.find(entry.getKey().workstationName());
			int capacity = workstation == null || workstation.workstation() == null ? 0
					: workstation.workstation().slots().stream()
					.filter(slot -> slot.role() == com.yeyito.littlechemistry.content.DynamicWorkstationSlotRole.OUTPUT)
					.mapToInt(com.yeyito.littlechemistry.content.DynamicWorkstationSlot::maxStack)
					.findFirst().orElse(0);
			return !entry.getValue().outputAvailable() || entry.getValue().outputCount() > capacity
					|| entry.getKey().referencesUnavailableDynamicContent();
		});
		return craftingRemoved || smeltingRemoved || workstationRemoved;
	}

	private void loadTables() throws IOException {
		if (!Files.isRegularFile(tablesFile)) return;
		JsonObject root = parseObject(tablesFile);
		int format = requireFormat(root, 1, TABLES_FORMAT);
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
			boolean recipeReady = format >= 2 && encoded.has("recipeReady")
					&& encoded.get("recipeReady").getAsBoolean();
			SharedCraftingContainer table = SharedCraftingContainer.physical(this, key, items, recipeReady);
			tables.put(key, table);
			if (table.hasGenerationParticles()) animatedTables.add(table);
		}
	}

	private void loadRecipes() throws IOException {
		if (!Files.isRegularFile(recipesFile)) return;
		JsonObject root = parseObject(recipesFile);
		int format = requireFormat(root, 1, RECIPES_FORMAT);
		if (format < RECIPES_FORMAT) recipesNeedRewrite = true;
		JsonArray encodedRecipes = root.getAsJsonArray("recipes");
		if (encodedRecipes == null || encodedRecipes.size() > 100_000) throw new IOException("Invalid AI recipe list");
		var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (JsonElement element : encodedRecipes) {
			if (!(element instanceof JsonObject encoded)) throw new IOException("Invalid AI recipe entry");
			String type = format >= 3 ? encoded.get("type").getAsString() : "crafting";
			boolean rejected = format >= 5 && "workstation".equals(type) && encoded.has("rejection");
			if (rejected && (encoded.has("output") || encoded.has("outputCount") || encoded.has("recipeData"))) {
				throw new IOException("Rejected AI workstation recipe cannot contain an output");
			}
			String output = rejected ? null : encoded.get("output").getAsString();
			if (!rejected && !output.matches("[a-z0-9_]{1,64}")) {
				throw new IOException("Invalid AI recipe output identifier");
			}
			double rawOutputCount = rejected ? 1.0
					: format >= 2 && encoded.has("outputCount") ? encoded.get("outputCount").getAsDouble() : 1.0;
			if (!Double.isFinite(rawOutputCount) || rawOutputCount != Math.rint(rawOutputCount)
					|| rawOutputCount < 1 || rawOutputCount > 64) {
				throw new IOException("Invalid AI recipe output count");
			}
			int outputCount = (int) rawOutputCount;
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
				case "workstation" -> {
					String workstation = encoded.get("workstation").getAsString();
					String process = encoded.get("process").getAsString();
					String discriminator = encoded.has("discriminator")
							? encoded.get("discriminator").getAsString() : "";
					JsonArray encodedIngredients = encoded.getAsJsonArray("ingredients");
					if (encodedIngredients == null || encodedIngredients.isEmpty()
							|| encodedIngredients.size() > WorkstationRecipeRequest.MAX_INGREDIENTS) {
						throw new IOException("Invalid AI workstation recipe ingredients");
					}
					List<WorkstationRecipeSignature.Ingredient> ingredients = new ArrayList<>();
					for (JsonElement ingredientElement : encodedIngredients) {
						JsonObject ingredient = ingredientElement.getAsJsonObject();
						ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops, ingredient.get("stack"))
								.getOrThrow(IOException::new);
						ingredients.add(new WorkstationRecipeSignature.Ingredient(
								ingredient.get("slot").getAsString(), stack,
								ingredient.get("count").getAsInt(),
								WorkstationRecipeRequest.IngredientUse.valueOf(
										ingredient.get("use").getAsString().toUpperCase(java.util.Locale.ROOT))));
					}
					WorkstationRecipeSignature signature = new WorkstationRecipeSignature(
							workstation, process, discriminator, ingredients);
					if (rejected) {
						try {
							workstationRecipes.put(signature, AiWorkstationRecipe.rejected(signature,
									WorkstationRecipeRejection.fromJson(encoded.getAsJsonObject("rejection"))));
						} catch (RuntimeException invalid) {
							throw new IOException("Invalid AI workstation recipe rejection", invalid);
						}
					} else {
						JsonObject recipeData = encoded.get("recipeData") instanceof JsonObject data
								? data.deepCopy() : new JsonObject();
						DynamicContentDefinition workstationDefinition = DynamicContentCatalog.find(workstation);
						if (workstationDefinition != null && workstationDefinition.workstation() != null) {
							workstationDefinition.workstation().recipeDataSchema().validateValue(recipeData);
						}
						workstationRecipes.put(signature,
								new AiWorkstationRecipe(signature, output, outputCount, recipeData));
					}
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

	private RecipeDisplayId assignRecipeDisplay(RecipeSignature signature, AiCraftingRecipe recipe) {
		RecipeDisplayId existing = recipeDisplayIds.get(signature);
		if (existing != null) {
			recipesByDisplayId.put(existing, recipe);
			return existing;
		}
		RecipeDisplayId assigned = new RecipeDisplayId(nextRecipeDisplayId--);
		recipeDisplayIds.put(signature, assigned);
		recipesByDisplayId.put(assigned, recipe);
		return assigned;
	}

	private static ClientboundRecipeBookAddPacket.Entry recipeBookEntry(RecipeDisplayId id, AiCraftingRecipe recipe) {
		if (id == null) return null;
		RecipeDisplay display = recipe.display().stream().findFirst().orElse(null);
		if (display == null) return null;
		RecipeDisplayEntry contents = new RecipeDisplayEntry(
				id,
				display,
				OptionalInt.empty(),
				RecipeBookCategories.CRAFTING_MISC,
				Optional.of(recipe.placementInfo().ingredients())
		);
		return new ClientboundRecipeBookAddPacket.Entry(contents, false, false);
	}

	private static CraftingInput craftingInput(AbstractCraftingMenu menu, List<Slot> grid) {
		return CraftingInput.of(menu.getGridWidth(), menu.getGridHeight(), grid.stream().map(Slot::getItem).toList());
	}

	private static int biggestCraftableStack(RecipeSignature signature, Inventory inventory, List<Slot> grid,
			UUID excludedPortableId) {
		int result = Integer.MAX_VALUE;
		List<ItemStack> ingredients = signature.ingredients();
		for (int index = 0; index < ingredients.size(); index++) {
			ItemStack ingredient = ingredients.get(index);
			if (ingredient.isEmpty()) continue;
			boolean seen = false;
			for (int earlier = 0; earlier < index; earlier++) {
				if (RecipeSignature.matchesIngredient(ingredient, ingredients.get(earlier))) {
					seen = true;
					break;
				}
			}
			if (seen) continue;

			int usesPerCraft = 0;
			for (ItemStack candidate : ingredients) {
				if (RecipeSignature.matchesIngredient(ingredient, candidate)) usesPerCraft++;
			}
			int available = countMatching(inventory.getNonEquipmentItems(), ingredient, excludedPortableId)
					+ countMatching(grid.stream().map(Slot::getItem).toList(), ingredient, excludedPortableId);
			result = Math.min(result, available / usesPerCraft);
			result = Math.min(result, ingredient.getMaxStackSize());
		}
		return result == Integer.MAX_VALUE ? 0 : result;
	}

	private static int countMatching(List<ItemStack> stacks, ItemStack ingredient, UUID excludedPortableId) {
		int count = 0;
		for (ItemStack stack : stacks) {
			if (!isPortableCarrier(stack, excludedPortableId)
					&& RecipeSignature.matchesIngredient(ingredient, stack)) count += stack.getCount();
		}
		return count;
	}

	private static ItemStack takeMatching(Inventory inventory, ItemStack ingredient, int amount,
			UUID excludedPortableId) {
		ItemStack taken = ItemStack.EMPTY;
		int remaining = amount;
		for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && remaining > 0; slot++) {
			ItemStack available = inventory.getItem(slot);
			if (isPortableCarrier(available, excludedPortableId)) continue;
			if (!RecipeSignature.matchesIngredient(ingredient, available)) continue;
			if (!taken.isEmpty() && !ItemStack.isSameItemSameComponents(taken, available)) continue;
			int count = Math.min(remaining, available.getCount());
			ItemStack removed = inventory.removeItem(slot, count);
			if (taken.isEmpty()) taken = removed;
			else taken.grow(removed.getCount());
			remaining -= removed.getCount();
		}
		if (remaining != 0) throw new IllegalStateException("Counted recipe ingredient disappeared during placement");
		return taken;
	}

	private static boolean isPortableCarrier(ItemStack stack, UUID tableId) {
		return tableId != null && tableId.equals(stack.get(PortableCraftingComponents.TABLE_ID));
	}

	private static void clearGridIntoInventory(Inventory inventory, List<Slot> grid) {
		for (Slot slot : grid) {
			ItemStack stack = slot.getItem().copy();
			if (!stack.isEmpty()) returnToMainInventory(inventory, stack);
			slot.set(ItemStack.EMPTY);
		}
		inventory.setChanged();
	}

	private static boolean canReturnGridToInventory(Inventory inventory, List<Slot> grid) {
		List<ItemStack> simulated = new ArrayList<>(
				inventory.getNonEquipmentItems().stream().map(ItemStack::copy).toList());
		for (Slot slot : grid) {
			ItemStack remaining = slot.getItem().copy();
			if (remaining.isEmpty()) continue;
			for (ItemStack target : simulated) {
				if (!remaining.isEmpty() && ItemStack.isSameItemSameComponents(target, remaining)
						&& target.getCount() < target.getMaxStackSize()) {
					int moved = Math.min(remaining.getCount(), target.getMaxStackSize() - target.getCount());
					target.grow(moved);
					remaining.shrink(moved);
				}
			}
			for (int index = 0; index < simulated.size() && !remaining.isEmpty(); index++) {
				if (!simulated.get(index).isEmpty()) continue;
				int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
				simulated.set(index, remaining.copyWithCount(moved));
				remaining.shrink(moved);
			}
			if (!remaining.isEmpty()) return false;
		}
		return true;
	}

	private static void returnToMainInventory(Inventory inventory, ItemStack remaining) {
		for (ItemStack target : inventory.getNonEquipmentItems()) {
			if (!remaining.isEmpty() && ItemStack.isSameItemSameComponents(target, remaining)
					&& target.getCount() < target.getMaxStackSize()) {
				int moved = Math.min(remaining.getCount(), target.getMaxStackSize() - target.getCount());
				target.grow(moved);
				remaining.shrink(moved);
			}
		}
		for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && !remaining.isEmpty(); slot++) {
			if (!inventory.getItem(slot).isEmpty()) continue;
			int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
			inventory.setItem(slot, remaining.split(moved));
		}
		if (!remaining.isEmpty()) inventory.player.drop(remaining, false);
	}

	private static NonNullList<ItemStack> portableItems(ItemStack carrier) {
		NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
		carrier.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		return items;
	}

	private boolean hasCraftingResult(List<ItemStack> items, ServerLevel level) {
		return !items.stream().allMatch(ItemStack::isEmpty)
				&& server.getRecipeManager().getRecipeFor(
						RecipeType.CRAFTING, CraftingInput.of(3, 3, items), level).isPresent();
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
			if (table.isRecipeReady()) encoded.addProperty("recipeReady", true);
			encodedTables.add(encoded);
		}
		root.add("tables", encodedTables);
		writeAtomically(tablesFile, root);
		tablesDirty = false;
	}

	private void saveRecipes(Map<RecipeSignature, AiCraftingRecipe> savedRecipes,
			Map<SmeltingRecipeSignature, AiSmeltingRecipe> savedSmeltingRecipes) throws IOException {
		saveRecipes(savedRecipes, savedSmeltingRecipes, workstationRecipes);
	}

	private void saveRecipes(Map<RecipeSignature, AiCraftingRecipe> savedRecipes,
			Map<SmeltingRecipeSignature, AiSmeltingRecipe> savedSmeltingRecipes,
			Map<WorkstationRecipeSignature, AiWorkstationRecipe> savedWorkstationRecipes) throws IOException {
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
		for (AiWorkstationRecipe recipe : savedWorkstationRecipes.values()) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("type", "workstation");
			encoded.addProperty("workstation", recipe.signature().workstationName());
			encoded.addProperty("process", recipe.signature().processId());
			if (!recipe.signature().discriminator().isEmpty()) {
				encoded.addProperty("discriminator", recipe.signature().discriminator());
			}
			JsonArray ingredients = new JsonArray();
			for (WorkstationRecipeSignature.Ingredient ingredient : recipe.signature().ingredients()) {
				JsonObject value = new JsonObject();
				value.addProperty("slot", ingredient.slotId());
				value.add("stack", ItemStack.OPTIONAL_CODEC.encodeStart(ops, ingredient.stack())
						.getOrThrow(IOException::new));
				value.addProperty("count", ingredient.count());
				value.addProperty("use", ingredient.use().name().toLowerCase(java.util.Locale.ROOT));
				ingredients.add(value);
			}
			encoded.add("ingredients", ingredients);
			if (recipe.isRejected()) {
				encoded.add("rejection", recipe.rejection().toJson());
			} else {
				encoded.addProperty("output", recipe.outputName());
				encoded.addProperty("outputCount", recipe.outputCount());
				encoded.add("recipeData", recipe.recipeData());
			}
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

	private record WorkstationKey(ResourceKey<Level> dimension, long packedPos) {
		private static WorkstationKey of(ServerLevel level, BlockPos pos) {
			return new WorkstationKey(level.dimension(), pos.asLong());
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

	private final class ActiveWorkstationJob {
		private final WorkstationRecipeSignature signature;
		private final Set<String> lockedSlotIds;
		private final Set<WorkstationKey> workstations = new HashSet<>();
		private final Set<UUID> requesters = new HashSet<>();
		private final CompletableFuture<RecipeGenerationAgent.GeneratedRecipe> promise = new CompletableFuture<>();
		private Future<?> task;

		private ActiveWorkstationJob(WorkstationRecipeSignature signature) {
			this.signature = signature;
			Set<String> lockedSlotIds = new HashSet<>();
			for (WorkstationRecipeSignature.Ingredient ingredient : signature.ingredients()) {
				lockedSlotIds.add(ingredient.slotId());
			}
			this.lockedSlotIds = Set.copyOf(lockedSlotIds);
		}
	}
}
