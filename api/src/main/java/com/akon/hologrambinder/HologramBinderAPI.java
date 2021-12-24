package com.akon.hologrambinder;

import com.akon.hologrambinder.event.internal.EntityTrackedPlayerAddEvent;
import com.akon.hologrambinder.event.internal.EntityTrackedPlayerRemoveEvent;
import com.akon.hologrambinder.internal.EntityTrackerUtil;
import com.akon.hologrambinder.internal.HologramImpl;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.NonNull;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class HologramBinderAPI {

	@Getter
	private final Plugin plugin;
	private final PacketAdapter packetListener;
	private final Listener bukkitListener;
	private final ConcurrentMap<Entity, ConcurrentMap<String, Hologram>> hologramsMap = new MapMaker().weakKeys().makeMap();
	private boolean disabled;

	/**
	 * @param plugin このAPIのインスタンスが使用するプラグイン(リスナーの登録等に使用されます)
	 */
	public HologramBinderAPI(Plugin plugin) {
		this.plugin = plugin;
		this.packetListener = new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_TELEPORT, PacketType.Play.Server.REL_ENTITY_MOVE, PacketType.Play.Server.REL_ENTITY_MOVE_LOOK) {

			@Override
			public void onPacketSending(PacketEvent e) {
				Optional<Collection<Hologram>> hologramsOpt = Optional.ofNullable(e.getPacket().getIntegers().read(0))
					.filter(entityId -> entityId >= 0)
					.map(entityId -> ProtocolLibrary.getProtocolManager().getEntityFromID(e.getPlayer().getWorld(), entityId))
					.map(HologramBinderAPI.this::getHolograms);
				if (e.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
					hologramsOpt.ifPresent(holograms -> holograms.forEach(hologram -> {
						StructureModifier<Double> doubleModifier = e.getPacket().getDoubles();
						((HologramImpl)hologram).move(e.getPlayer(), doubleModifier.read(0), doubleModifier.read(1), doubleModifier.read(2));
					}));
				} else {
					hologramsOpt.ifPresent(holograms -> holograms.forEach(hologram -> {
						StructureModifier<Short> shortModifier = e.getPacket().getShorts();
						((HologramImpl)hologram).moveRel(e.getPlayer(), shortModifier.read(0), shortModifier.read(1), shortModifier.read(2));
					}));
				}
			}

		};
		this.bukkitListener = new Listener() {

			@EventHandler
			public void onWorldInit(WorldInitEvent e) {
				EntityTrackerUtil.EntityTrackerInjector.inject(e.getWorld());
			}

			@EventHandler
			public void onTrackedPlayerAdd(EntityTrackedPlayerAddEvent e) {
				HologramBinderAPI.this.getHolograms(e.getEntity()).forEach(hologram -> ((HologramImpl)hologram).display(e.getPlayer()));
			}

			@EventHandler
			public void onTrackedPlayerRemove(EntityTrackedPlayerRemoveEvent e) {
				HologramBinderAPI.this.getHolograms(e.getEntity()).forEach(hologram -> ((HologramImpl)hologram).delete(e.getPlayer()));
			}

		};
		Bukkit.getWorlds().forEach(EntityTrackerUtil.EntityTrackerInjector::inject);
		Bukkit.getPluginManager().registerEvents(this.bukkitListener, plugin);
		ProtocolLibrary.getProtocolManager().addPacketListener(this.packetListener);
	}

	/**
	 * エンティティにホログラムをバインドします
	 * @param entity ホログラムをバインドするエンティティ
	 * @param id ホログラムのID(エンティティごとに管理されます)
	 * @param offsetX エンティティからの相対的なX座標
	 * @param offsetY エンティティからの相対的なY座標
	 * @param offsetZ エンティティからの相対的なZ座標
	 * @param text ホログラムに表示するテキスト
	 * @return 作成されたホログラムのインスタンス
	 */
	@Nullable
	public Hologram bindHologram(Entity entity, String id, double offsetX, double offsetY, double offsetZ, @NonNull BaseComponent... text) {
		this.checkDisabled();
		HologramImpl hologram = new HologramImpl(this, id, text, offsetX, offsetY, offsetZ, entity);
		if (this.hologramsMap.computeIfAbsent(entity, key -> Maps.newConcurrentMap()).putIfAbsent(id, hologram) == null) {
			EntityTrackerUtil.getTrackedPlayersView(entity).forEach(hologram::display);
			return hologram;
		}
		return null;
	}

	/**
	 * エンティティにバインドされているホログラムをIDから取得します
	 * @param entity ホログラムを取得するエンティティ
	 * @param id 取得するホログラムのID
	 * @return 取得されたホログラム 存在しなかった場合はnull
	 */
	@Nullable
	public Hologram getHologram(Entity entity, String id) {
		this.checkDisabled();
		return Optional.ofNullable(this.hologramsMap.get(entity)).map(map -> map.get(id)).orElse(null);
	}

	/**
	 * エンティティにバインドされているホログラムを削除します
	 * @param entity ホログラムを削除するエンティティ
	 * @param id 削除するホログラムのID
	 * @return 削除されたホログラム 存在せずに削除できなかった場合はnull
	 */
	@Nullable
	public Hologram removeHologram(Entity entity, String id) {
		this.checkDisabled();
		return Optional.ofNullable(this.hologramsMap.get(entity))
			.map(map -> map.remove(id))
			.map(hologram -> {
				EntityTrackerUtil.getTrackedPlayersView(entity).forEach(((HologramImpl)hologram)::delete);
				return hologram;
			})
			.orElse(null);
	}

	/**
	 * エンティティにバインドされているホログラムを全て取得します
	 * @param entity ホログラムを取得するエンティティ
	 * @return エンティティにバインドされているすべてのホログラム 一つも存在しない場合は空のコレクション
	 */
	public Collection<Hologram> getHolograms(Entity entity) {
		this.checkDisabled();
		return Optional.ofNullable(this.hologramsMap.get(entity))
			.map(Map::values)
			.<Set<Hologram>>map(ImmutableSet::copyOf)
			.orElse(Collections.emptySet());
	}

	/**
	 * このAPIインスタンスを無効化し使用できなくします
	 */
	public void disable() {
		this.checkDisabled();
		this.disabled = true;
		Bukkit.getWorlds().forEach(EntityTrackerUtil.EntityTrackerInjector::remove);
		HandlerList.getHandlerLists().forEach(handlerList -> handlerList.unregister(this.bukkitListener));
		ProtocolLibrary.getProtocolManager().removePacketListener(this.packetListener);
	}

	private void checkDisabled() {
		if (this.disabled) {
			throw new IllegalStateException("This API has been disabled.");
		}
	}

}
