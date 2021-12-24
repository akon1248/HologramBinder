package com.akon.hologrambinder.event;

import com.akon.hologrambinder.Hologram;
import lombok.Getter;
import lombok.NonNull;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * プレイヤーにホログラムのテキストが表示されるときにトリガーされます
 */
@Getter
public class HologramTextEvent extends Event {

	private static final HandlerList HANDLER_LIST = new HandlerList();

	/**
	 * テキストを表示しているホログラム
	 */
	private final Hologram hologram;
	/**
	 * テキストを表示させるプレイヤー
	 */
	private final Player player;
	/**
	 * 表示されるテキスト
	 */
	@NonNull
	private BaseComponent[] text;

	public HologramTextEvent(Hologram hologram, Player player) {
		super(!Bukkit.isPrimaryThread());
		this.hologram = hologram;
		this.player = player;
		this.text = hologram.getText();
	}

	/**
	 * 表示されるテキストを変更します
	 * @param text 表示されるテキスト
	 */
	public void setText(BaseComponent... text) {
		this.text = text;
	}

	@Override
	@NotNull
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}

}
