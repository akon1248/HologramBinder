package com.akon.hologrambinder.event;

import com.akon.hologrambinder.Hologram;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * ホログラムがプレイヤーに表示される際にトリガーされます
 */
@Getter
public class HologramDisplayEvent extends Event implements Cancellable {

	private static final HandlerList HANDLER_LIST = new HandlerList();

	/**
	 * ホログラムが表示されるプレイヤー
	 */
	private final Player player;
	/**
	 * 表示されるホログラム
	 */
	private final Hologram hologram;
	@Setter
	private boolean cancelled;

	public HologramDisplayEvent(Player player, Hologram hologram) {
		super(!Bukkit.isPrimaryThread());
		this.player = player;
		this.hologram = hologram;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}

}
