package com.akon.hologrambinder.internal;

import com.akon.hologrambinder.Hologram;
import com.akon.hologrambinder.HologramBinderAPI;
import com.akon.hologrambinder.event.HologramDisplayEvent;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

@Getter
public class HologramImpl implements Hologram {

	@Getter(AccessLevel.NONE)
	private final HologramPacketFactory packetFactory = new HologramPacketFactory();
	@Getter(AccessLevel.NONE)
	private final HologramBinderAPI api;
	private final String id;
	@NonNull
	private BaseComponent[] text;
	private double offsetX;
	private double offsetY;
	private double offsetZ;
	private final Entity entity;

	public HologramImpl(HologramBinderAPI api, String id, @NonNull BaseComponent[] text, double offsetX, double offsetY, double offsetZ, Entity entity) {
		this.api = api;
		this.id = id;
		this.text = Arrays.copyOf(text, text.length);
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		this.entity = entity;
	}

	@Override
	public BaseComponent[] getText() {
		return Arrays.copyOf(this.text, this.text.length);
	}

	@Override
	public void setText(@NonNull BaseComponent... text) {
		this.text = Arrays.copyOf(text, text.length);
		EntityTrackerUtil.getTrackedPlayersView(this.entity).forEach(player -> sendPacket(player, this.packetFactory.createMetadataPacket(this, player)));
	}

	@Override
	public void setOffset(double offsetX, double offsetY, double offsetZ) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		Location loc = this.entity.getLocation();
		PacketContainer packet = this.packetFactory.createPositionPacket(this, loc.getX(), loc.getY(), loc.getZ());
		EntityTrackerUtil.getTrackedPlayersView(this.entity).forEach(player -> sendPacket(player, packet));
	}

	@Override
	public void show(Player player) {
		if (this.isBound() && EntityTrackerUtil.isTracked(player, this.entity)) {
			this.display(player);
		}
	}

	@Override
	public void hide(Player player) {
		if (this.isBound() && EntityTrackerUtil.isTracked(player, this.entity)) {
			this.delete(player);
		}
	}

	public void move(Player player, double x, double y, double z) {
		sendPacket(player, this.packetFactory.createPositionPacket(this, x, y, z));
	}

	public void moveRel(Player player, short dx, short dy, short dz) {
		sendPacket(player, this.packetFactory.createRelMovePacket(dx, dy, dz));
	}

	public void display(Player player) {
		HologramDisplayEvent event = new HologramDisplayEvent(player, this);
		Bukkit.getPluginManager().callEvent(event);
		if (!event.isCancelled()) {
			sendPacket(player, this.packetFactory.createSpawnPacket(this, this.entity));
			sendPacket(player, this.packetFactory.createMetadataPacket(this, player));
		}
	}

	public void delete(Player player) {
		sendPacket(player, this.packetFactory.createDestroyPacket());
	}

	@SneakyThrows(InvocationTargetException.class)
	private static void sendPacket(Player player, PacketContainer packet) {
		ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
	}

	private boolean isBound() {
		return this.api.getHologram(this.entity, this.id) == this;
	}

}
