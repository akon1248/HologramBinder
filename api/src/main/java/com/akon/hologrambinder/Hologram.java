package com.akon.hologrambinder;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * エンティティにバインドされたホログラム
 */
public interface Hologram {

	/**
	 * ホログラムのIDを取得します
	 * @return このホログラムのID
	 */
	String getId();

	/**
	 * ホログラムに表示されているテキストを取得します
	 * @return このホログラムに表示されているテキスト
	 */
	BaseComponent[] getText();

	/**
	 * ホログラムに表示するテキストを変更します
	 * @param text このホログラムに表示するテキスト
	 */
	void setText(BaseComponent... text);

	/**
	 * バインドされているエンティティからの相対的なX座標を取得します
	 * @return このホログラムがバインドされているエンティティからの相対的なX座標
	 */
	double getOffsetX();

	/**
	 * バインドされているエンティティからの相対的なY座標を取得します
	 * @return このホログラムがバインドされているエンティティからの相対的なY座標
	 */
	double getOffsetY();

	/**
	 * バインドされているエンティティからの相対的なZ座標を取得します
	 * @return このホログラムがバインドされているエンティティからの相対的なZ座標
	 */
	double getOffsetZ();

	/**
	 * バインドされているエンティティからの相対的な座標を変更します
	 * @param offsetX エンティティからの相対的なX座標
	 * @param offsetY エンティティからの相対的なY座標
	 * @param offsetZ エンティティからの相対的なZ座標
	 */
	void setOffset(double offsetX, double offsetY, double offsetZ);

	/**
	 * ホログラムがバインドされているエンティティを取得します
	 * @return このホログラムがバインドされているエンティティ
	 */
	Entity getEntity();

	/**
	 * {@link Hologram#hide(Player)}や{@link com.akon.hologrambinder.event.HologramDisplayEvent}がキャンセルされることで非表示になっているホログラムを表示させます
	 * このメソッドは{@link com.akon.hologrambinder.event.HologramDisplayEvent}をトリガーします
	 * @param player このホログラムを表示するプレイヤー
	 */
	void show(Player player);

	/**
	 * ホログラムを一時的に非表示にします
	 * この状態が保存されることはありません
	 * {@link com.akon.hologrambinder.event.HologramDisplayEvent}がトリガーされる際に、このホログラムは再び表示される可能性があります
	 * @param player このホログラムを非表示にするプレイヤー
	 */
	void hide(Player player);

}
