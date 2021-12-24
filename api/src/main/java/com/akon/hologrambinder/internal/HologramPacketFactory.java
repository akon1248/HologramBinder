package com.akon.hologrambinder.internal;

import com.akon.hologrambinder.Hologram;
import com.akon.hologrambinder.event.HologramTextEvent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.google.common.collect.Lists;
import lombok.Data;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class HologramPacketFactory {

	private static final AtomicInteger ID_COUNTER = new AtomicInteger(-1);
	private static final WrappedDataWatcher.WrappedDataWatcherObject GENERAL_FLAGS;
	private static final WrappedDataWatcher.WrappedDataWatcherObject CUSTOM_NAME;
	private static final WrappedDataWatcher.WrappedDataWatcherObject CUSTOM_NAME_VISIBLE;
	private static final WrappedDataWatcher.WrappedDataWatcherObject NO_GRAVITY;
	private static final WrappedDataWatcher.WrappedDataWatcherObject ARMOR_STAND_FLAGS;

	static {
		WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
		WrappedDataWatcher.Serializer booleanSerializer = WrappedDataWatcher.Registry.get(Boolean.class);
		GENERAL_FLAGS = new WrappedDataWatcher.WrappedDataWatcherObject(0, byteSerializer);
		CUSTOM_NAME = new WrappedDataWatcher.WrappedDataWatcherObject(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true));
		CUSTOM_NAME_VISIBLE = new WrappedDataWatcher.WrappedDataWatcherObject(3, booleanSerializer);
		NO_GRAVITY = new WrappedDataWatcher.WrappedDataWatcherObject(5, booleanSerializer);
		ARMOR_STAND_FLAGS = new WrappedDataWatcher.WrappedDataWatcherObject(14, byteSerializer);
	}

	private final int entityId = ID_COUNTER.getAndDecrement();
	private final UUID uuid = UUID.randomUUID();

	public PacketContainer createSpawnPacket(Hologram hologram, Entity entity) {
		PacketContainer packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);
		packet.getModifier().writeDefaults();
		packet.getIntegers().write(0, this.entityId).write(1, 1);
		packet.getUUIDs().write(0, this.uuid);
		Location loc = entity.getLocation();
		packet.getDoubles()
			.write(0, loc.getX()+hologram.getOffsetX())
			.write(1, loc.getY()+hologram.getOffsetY())
			.write(2, loc.getZ()+hologram.getOffsetZ());
		return packet;
	}

	public PacketContainer createDestroyPacket() {
		PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
		packet.getIntegerArrays().write(0, new int[]{this.entityId});
		return packet;
	}

	public PacketContainer createMetadataPacket(Hologram hologram, Player player) {
		PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
		HologramTextEvent event = new HologramTextEvent(hologram, player);
		Bukkit.getPluginManager().callEvent(event);
		packet.getModifier().writeDefaults();
		ArrayList<WrappedWatchableObject> watchableObjects = Lists.newArrayList();
		watchableObjects.add(new WrappedWatchableObject(GENERAL_FLAGS, (byte)0x20));
		watchableObjects.add(new WrappedWatchableObject(CUSTOM_NAME, Optional.ofNullable(WrappedChatComponent.fromJson(ComponentSerializer.toString(event.getText())).getHandle())));
		watchableObjects.add(new WrappedWatchableObject(CUSTOM_NAME_VISIBLE, true));
		watchableObjects.add(new WrappedWatchableObject(NO_GRAVITY, true));
		watchableObjects.add(new WrappedWatchableObject(ARMOR_STAND_FLAGS, (byte)0b11001));
		packet.getIntegers().write(0, this.entityId);
		packet.getWatchableCollectionModifier().write(0, watchableObjects);
		return packet;
	}

	public PacketContainer createPositionPacket(Hologram hologram, double x, double y, double z) {
		PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
		packet.getModifier().writeDefaults();
		packet.getIntegers().write(0, this.entityId);
		packet.getDoubles()
			.write(0, x+hologram.getOffsetX())
			.write(1, y+hologram.getOffsetY())
			.write(2, z+hologram.getOffsetZ());
		return packet;
	}

	public PacketContainer createRelMovePacket(short dx, short dy, short dz) {
		PacketContainer packet = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE);
		packet.getModifier().writeDefaults();
		packet.getIntegers().write(0, this.entityId);
		packet.getShorts()
			.write(0, dx)
			.write(1, dy)
			.write(2, dz);
		packet.getBooleans().write(2, true);
		return packet;
	}

}
