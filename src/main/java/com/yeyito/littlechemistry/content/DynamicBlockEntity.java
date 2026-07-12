package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class DynamicBlockEntity extends BlockEntity {
	private Identifier contentId;

	public DynamicBlockEntity(BlockPos position, BlockState state) {
		super(DynamicContentObjects.BLOCK_ENTITY_TYPE, position, state);
	}

	public Identifier contentId() {
		return contentId;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		contentId = input.read("content_id", Identifier.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("content_id", Identifier.CODEC, contentId);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		contentId = components.get(DynamicContentObjects.CONTENT_ID);
		setChanged();
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (contentId != null) {
			builder.set(DynamicContentObjects.CONTENT_ID, contentId);
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
		return saveWithoutMetadata(lookup);
	}
}
