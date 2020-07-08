package com.simibubi.create.content.logistics.block.belts.tunnel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.block.belts.tunnel.BeltTunnelBlock.Shape;
import com.simibubi.create.foundation.gui.widgets.InterpolatedChasingValue;
import com.simibubi.create.foundation.tileEntity.SmartTileEntity;
import com.simibubi.create.foundation.tileEntity.TileEntityBehaviour;
import com.simibubi.create.foundation.utility.Iterate;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.IntNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.Direction.AxisDirection;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

public class BeltTunnelTileEntity extends SmartTileEntity {

	public HashMap<Direction, InterpolatedChasingValue> flaps;
	protected LazyOptional<IItemHandler> cap = LazyOptional.empty();
	protected List<Pair<Direction, Boolean>> flapsToSend;

	public BeltTunnelTileEntity(TileEntityType<? extends BeltTunnelTileEntity> type) {
		super(type);
		flaps = new HashMap<>();
		flapsToSend = new LinkedList<>();
	}

	@Override
	public void remove() {
		super.remove();
		cap.invalidate();
	}

	@Override
	public CompoundNBT write(CompoundNBT compound) {
		ListNBT flapsNBT = new ListNBT();
		for (Direction direction : flaps.keySet())
			flapsNBT.add(IntNBT.of(direction.getIndex()));
		compound.put("Flaps", flapsNBT);
		return super.write(compound);
	}

	@Override
	public void read(CompoundNBT compound) {
		Set<Direction> newFlaps = new HashSet<>(6);
		ListNBT flapsNBT = compound.getList("Flaps", NBT.TAG_INT);
		for (INBT inbt : flapsNBT)
			if (inbt instanceof IntNBT)
				newFlaps.add(Direction.byIndex(((IntNBT) inbt).getInt()));
		
		for (Direction d : Iterate.directions) 
			if (!newFlaps.contains(d))
				flaps.remove(d);
			else if (!flaps.containsKey(d))
				flaps.put(d, new InterpolatedChasingValue().start(.25f)
					.target(0)
					.withSpeed(.05f));

		super.read(compound);
	}

	@Override
	public CompoundNBT writeToClient(CompoundNBT tag) {
		CompoundNBT writeToClient = super.writeToClient(tag);
		if (!flapsToSend.isEmpty()) {
			ListNBT flapsNBT = new ListNBT();
			for (Pair<Direction, Boolean> pair : flapsToSend) {
				CompoundNBT flap = new CompoundNBT();
				flap.putInt("Flap", pair.getKey()
					.getIndex());
				flap.putBoolean("FlapInward", pair.getValue());
				flapsNBT.add(flap);
			}
			writeToClient.put("TriggerFlaps", flapsNBT);
			flapsToSend.clear();
		}
		return writeToClient;
	}

	@Override
	public void readClientUpdate(CompoundNBT tag) {
		super.readClientUpdate(tag);
		if (tag.contains("TriggerFlaps")) {
			ListNBT flapsNBT = tag.getList("TriggerFlaps", NBT.TAG_COMPOUND);
			for (INBT inbt : flapsNBT) {
				CompoundNBT flap = (CompoundNBT) inbt;
				Direction side = Direction.byIndex(flap.getInt("Flap"));
				flap(side, flap.getBoolean("FlapInward"));
			}
		}
	}

	public void updateTunnelConnections() {
		flaps.clear();
		BlockState tunnelState = getBlockState();
		for (Direction direction : Direction.values()) {
			if (direction.getAxis()
				.isVertical())
				continue;
			BlockState blockState = world.getBlockState(pos.offset(direction));
			if (blockState.getBlock() instanceof BeltTunnelBlock)
				continue;
			if (direction.getAxis() != tunnelState.get(BlockStateProperties.HORIZONTAL_AXIS)) {
				boolean positive =
					direction.getAxisDirection() == AxisDirection.POSITIVE ^ direction.getAxis() == Axis.Z;
				Shape shape = tunnelState.get(BeltTunnelBlock.SHAPE);
				if (BeltTunnelBlock.isStraight(tunnelState))
					continue;
				if (positive && shape == Shape.T_LEFT)
					continue;
				if (!positive && shape == Shape.T_RIGHT)
					continue;
			}
			flaps.put(direction, new InterpolatedChasingValue().start(.25f)
				.target(0)
				.withSpeed(.05f));
		}
		sendData();
	}

	public void flap(Direction side, boolean inward) {
		if (world.isRemote) {
			if (flaps.containsKey(side))
				flaps.get(side)
					.set(inward ? -1 : 1);
			return;
		}

		flapsToSend.add(Pair.of(side, inward));
	}

	@Override
	public void initialize() {
		super.initialize();
//		updateTunnelConnections();
	}

	@Override
	public void tick() {
		super.tick();
		if (!world.isRemote) {
			if (!flapsToSend.isEmpty())
				sendData();
			return;
		}
		flaps.forEach((d, value) -> value.tick());
	}

	@Override
	public void addBehaviours(List<TileEntityBehaviour> behaviours) {}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
		if (capability != CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
			return super.getCapability(capability, side);

		if (!this.cap.isPresent()) {
			if (AllBlocks.BELT.has(world.getBlockState(pos.down()))) {
				TileEntity teBelow = world.getTileEntity(pos.down());
				if (teBelow != null) {
					T capBelow = teBelow.getCapability(capability, Direction.UP)
						.orElse(null);
					if (capBelow != null) {
						cap = LazyOptional.of(() -> capBelow)
							.cast();
					}
				}
			}
		}
		return this.cap.cast();
	}

}
