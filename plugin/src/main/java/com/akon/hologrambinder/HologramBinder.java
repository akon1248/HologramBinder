package com.akon.hologrambinder;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.dependency.Dependency;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

@Plugin(name = "HologramBinder", version = "1.0")
@Dependency("ProtocolLib")
@Author("akon")
public final class HologramBinder extends JavaPlugin {

	@Getter
	private static HologramBinderAPI api;

	@Override
	public void onEnable() {
		api = new HologramBinderAPI(this);
	}

	@Override
	public void onDisable() {
		api.disable();
	}

}
