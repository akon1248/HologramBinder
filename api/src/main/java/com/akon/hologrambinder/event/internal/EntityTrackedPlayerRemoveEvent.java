package com.akon.hologrambinder.event.internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
@Getter
public class EntityTrackedPlayerRemoveEvent extends Event {

	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final Entity entity;
	private final Player player;

	@Override
	@NotNull
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}

}
