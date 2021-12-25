package com.akon.hologrambinder.internal;

import com.akon.hologrambinder.event.internal.EntityTrackedPlayerAddEvent;
import com.akon.hologrambinder.event.internal.EntityTrackedPlayerRemoveEvent;
import com.akon.hologrambinder.util.LazyValue;
import com.comphenix.net.bytebuddy.ByteBuddy;
import com.comphenix.net.bytebuddy.description.modifier.FieldManifestation;
import com.comphenix.net.bytebuddy.description.modifier.Visibility;
import com.comphenix.net.bytebuddy.description.type.TypeDescription;
import com.comphenix.net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import com.comphenix.net.bytebuddy.implementation.MethodCall;
import com.comphenix.net.bytebuddy.matcher.ElementMatchers;
import com.comphenix.protocol.injector.BukkitUnwrapper;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.ConstructorAccessor;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.reflect.fuzzy.FuzzyMethodContract;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.google.common.collect.Collections2;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

@UtilityClass
public class EntityTrackerUtil {

	private final Class<?> NMS_PLAYER_CLASS = MinecraftReflection.getEntityPlayerClass();
	private final Class<?> PLAYER_CHUNK_MAP_CLASS;
	private final Class<?> ENTITY_TRACKER_CLASS;
	private final MethodAccessor GET_CHUNK_PROVIDER;
	private final FieldAccessor PLAYER_CHUNK_MAP;
	private final FieldAccessor TRACKED_ENTITIES;
	private final FieldAccessor TRACKER;
	private final FieldAccessor TRACKED_PLAYERS;
	//Paper only
	private final FieldAccessor TRACKED_PLAYER_MAP;

	static {
		ENTITY_TRACKER_CLASS = MinecraftReflection.getEntityTrackerClass();
		Class<?> nmsWorldClass = MinecraftReflection.getWorldServerClass();
		Class<?> chunkProviderServerClass = MinecraftReflection.getChunkProviderServer();
		PLAYER_CHUNK_MAP_CLASS = MinecraftReflection.getPlayerChunkMap();
		GET_CHUNK_PROVIDER = Accessors.getMethodAccessor(FuzzyReflection.fromClass(nmsWorldClass).getMethod(FuzzyMethodContract.newBuilder()
			.returnDerivedOf(chunkProviderServerClass)
			.parameterCount(0)
			.build()
		));
		PLAYER_CHUNK_MAP = Accessors.getFieldAccessor(FuzzyReflection.fromClass(chunkProviderServerClass).getFieldByType("playerChunkMap", PLAYER_CHUNK_MAP_CLASS));
		TRACKED_ENTITIES = Accessors.getFieldAccessor(FuzzyReflection.fromClass(PLAYER_CHUNK_MAP_CLASS).getParameterizedField(Int2ObjectMap.class, ENTITY_TRACKER_CLASS));
		TRACKER = Accessors.getFieldAccessor(FuzzyReflection.fromClass(ENTITY_TRACKER_CLASS, true).getFieldByType("tracker", MinecraftReflection.getEntityClass()));
		TRACKED_PLAYERS = Accessors.getFieldAccessor(FuzzyReflection.fromClass(ENTITY_TRACKER_CLASS).getParameterizedField(Set.class, NMS_PLAYER_CLASS));
		FieldAccessor trackedPlayerMap;
		try {
			trackedPlayerMap = Accessors.getFieldAccessor(FuzzyReflection.fromClass(ENTITY_TRACKER_CLASS).getParameterizedField(Map.class, NMS_PLAYER_CLASS, Boolean.class));
		} catch (Exception ignored) {
			trackedPlayerMap = null;
		}
		TRACKED_PLAYER_MAP = trackedPlayerMap;
	}

	private Object getPlayerChunkMap(World world) {
		Object nmsWorld = BukkitUnwrapper.getInstance().unwrapItem(world);
		return PLAYER_CHUNK_MAP.get(GET_CHUNK_PROVIDER.invoke(nmsWorld));
	}

	private Optional<Set<?>> getTrackedPlayers(Entity entity) {
		return Optional.ofNullable(((Int2ObjectMap<?>)TRACKED_ENTITIES.get(getPlayerChunkMap(entity.getWorld()))).get(entity.getEntityId()))
			.map(entityTracker -> (Set<?>)TRACKED_PLAYERS.get(entityTracker));
	}

	public Collection<Player> getTrackedPlayersView(Entity entity) {
		return getTrackedPlayers(entity)
			.map(trackedPlayers -> Collections2.transform(trackedPlayers, nmsPlayer -> (Player)MinecraftReflection.getBukkitEntity(nmsPlayer)))
			.map(Collections::unmodifiableCollection)
			.orElse(Collections.emptySet());
	}

	public boolean isTracked(Player player, Entity entity) {
		return getTrackedPlayers(entity)
			.filter(trackedPlayers -> trackedPlayers.contains(BukkitUnwrapper.getInstance().unwrapItem(player)))
			.isPresent();
	}

	@UtilityClass
	//コレクションをラップし、特定のメソッドの呼び出しに割り込みさせる
	public class EntityTrackerInjector {

		private final Class<?> TRACKED_ENTITIES_INJECTOR_CLASS;
		private final ConstructorAccessor TRACKED_ENTITIES_INJECTOR_CONSTRUCTOR;
		private final MethodAccessor UNWRAP_TRACKED_ENTITIES_INJECTOR;
		private final Class<?> TRACKED_PLAYERS_INJECTOR_CLASS;
		private final ConstructorAccessor TRACKED_PLAYERS_INJECTOR_CONSTRUCTOR;
		private final MethodAccessor UNWRAP_TRACKED_PLAYERS_INJECTOR;
		//Paper only
		private final Class<?> TRACKED_PLAYER_MAP_INJECTOR_CLASS;
		private final ConstructorAccessor TRACKED_PLAYER_MAP_INJECTOR_CONSTRUCTOR;
		private final MethodAccessor UNWRAP_TRACKED_PLAYER_MAP_INJECTOR;

		static {
			MethodCall superConstructorCall = MethodCall.invoke(new TypeDescription.ForLoadedType(Object.class).getDeclaredMethods().filter(ElementMatchers.isConstructor()).getOnly()).onSuper();
			TypeDescription.Generic entityTrackerMapType = TypeDescription.Generic.Builder.parameterizedType(Int2ObjectMap.class, ENTITY_TRACKER_CLASS).build();
			TRACKED_ENTITIES_INJECTOR_CLASS = new ByteBuddy()
				.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
				.implement(entityTrackerMapType)
				.defineField("delegate", entityTrackerMapType, Visibility.PRIVATE, FieldManifestation.FINAL)
				.defineConstructor(Visibility.PUBLIC)
				.withParameters(entityTrackerMapType)
				.intercept(superConstructorCall.andThen(com.comphenix.net.bytebuddy.implementation.FieldAccessor.ofField("delegate").setsArgumentAt(0)))
				.method(
					ElementMatchers.not(
						ElementMatchers.isDeclaredBy(Object.class)
							.or(
								ElementMatchers.named("put")
									.and(
										ElementMatchers.takesArgument(0, int.class)
									)
							)
					)
					.or(
						ElementMatchers.isDeclaredBy(Object.class)
							.and(
								ElementMatchers.named("equals")
									.or(ElementMatchers.named("hashCode"))
									.or(ElementMatchers.named("toString"))
							)
					)
				)
				.intercept(MethodCall.invokeSelf().onField("delegate").withAllArguments())
				.method(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class)).and(ElementMatchers.named("put")).and(ElementMatchers.takesArgument(0, int.class)))
				.intercept(
					insertHook("hookPut")
						.withArgument(1)
						.andThen(
							MethodCall.invokeSelf().onField("delegate").withAllArguments()
						)
				)
				.defineMethod("unwrap", entityTrackerMapType, Visibility.PUBLIC)
				.intercept(com.comphenix.net.bytebuddy.implementation.FieldAccessor.ofField("delegate"))
				.make()
				.load(EntityTrackerInjector.class.getClassLoader())
				.getLoaded();
			TRACKED_ENTITIES_INJECTOR_CONSTRUCTOR = Accessors.getConstructorAccessor(TRACKED_ENTITIES_INJECTOR_CLASS, Int2ObjectMap.class);
			UNWRAP_TRACKED_ENTITIES_INJECTOR = Accessors.getMethodAccessor(TRACKED_ENTITIES_INJECTOR_CLASS, "unwrap");
			TypeDescription.Generic nmsPlayerSetType = TypeDescription.Generic.Builder.parameterizedType(Set.class, NMS_PLAYER_CLASS).build();
			TRACKED_PLAYERS_INJECTOR_CLASS = new ByteBuddy()
				.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
				.implement(nmsPlayerSetType)
				.defineField("delegate", nmsPlayerSetType, Visibility.PRIVATE, FieldManifestation.FINAL)
				.defineField("entity", Entity.class, Visibility.PRIVATE, FieldManifestation.FINAL)
				.defineConstructor(Visibility.PUBLIC)
				.withParameters(nmsPlayerSetType, new TypeDescription.ForLoadedType(Entity.class))
				.intercept(superConstructorCall
						.andThen(com.comphenix.net.bytebuddy.implementation.FieldAccessor.ofField("delegate").setsArgumentAt(0))
						.andThen(com.comphenix.net.bytebuddy.implementation.FieldAccessor.ofField("entity").setsArgumentAt(1))
				)
				.defineMethod("unwrap", nmsPlayerSetType, Visibility.PUBLIC)
				.intercept(com.comphenix.net.bytebuddy.implementation.FieldAccessor.ofField("delegate"))
				.method(ElementMatchers.isDeclaredBy(Set.class)
					.and(
						ElementMatchers.not(
							ElementMatchers.named("add")
								.or(ElementMatchers.named("remove"))
								.or(ElementMatchers.named("iterator"))
							)
					)
					.or(
						ElementMatchers.isDeclaredBy(Object.class)
							.and(
								ElementMatchers.named("equals")
									.or(ElementMatchers.named("hashCode"))
									.or(ElementMatchers.named("toString"))
							)
					)
				)
				.intercept(MethodCall.invokeSelf().onField("delegate").withAllArguments())
				.method(ElementMatchers.isDeclaredBy(Set.class).and(ElementMatchers.named("add")))
				.intercept(
					insertHook("hookAdd")
						.withField("delegate")
						.withField("entity")
						.withArgument(0)
				)
				.method(ElementMatchers.isDeclaredBy(Set.class).and(ElementMatchers.named("remove")))
				.intercept(
					insertHook("hookRemove")
						.withField("delegate")
						.withField("entity")
						.withArgument(0)
				)
				.method(ElementMatchers.isDeclaredBy(Set.class).and(ElementMatchers.named("iterator")))
				.intercept(
					insertHook("hookIterator")
						.withField("delegate")
						.withField("entity")
				)
				.make()
				.load(EntityTrackerInjector.class.getClassLoader())
				.getLoaded();
			TRACKED_PLAYERS_INJECTOR_CONSTRUCTOR = Accessors.getConstructorAccessor(TRACKED_PLAYERS_INJECTOR_CLASS, Set.class, Entity.class);
			UNWRAP_TRACKED_PLAYERS_INJECTOR = Accessors.getMethodAccessor(TRACKED_PLAYERS_INJECTOR_CLASS, "unwrap");
			if (TRACKED_PLAYER_MAP == null) {
				TRACKED_PLAYER_MAP_INJECTOR_CLASS = null;
				TRACKED_PLAYER_MAP_INJECTOR_CONSTRUCTOR = null;
				UNWRAP_TRACKED_PLAYER_MAP_INJECTOR = null;
			} else {
				TypeDescription.Generic mapType = TypeDescription.Generic.Builder.parameterizedType(Map.class, NMS_PLAYER_CLASS, Boolean.class).build();
				TRACKED_PLAYER_MAP_INJECTOR_CLASS = new ByteBuddy()
					.subclass(Object.class)
					.implement(mapType)
					.defineField("delegate", mapType, Visibility.PRIVATE, FieldManifestation.FINAL)
					.defineField("entity", Entity.class, Visibility.PRIVATE, FieldManifestation.FINAL)
					.defineConstructor(Visibility.PUBLIC)
					.withParameters(mapType, new TypeDescription.ForLoadedType(Entity.class))
					.intercept(superConstructorCall
						.andThen(com.comphenix.net.bytebuddy.implementation.FieldAccessor.ofField("delegate").setsArgumentAt(0))
						.andThen(com.comphenix.net.bytebuddy.implementation.FieldAccessor.ofField("entity").setsArgumentAt(1))
					)
					.defineMethod("unwrap", mapType, Visibility.PUBLIC)
					.intercept(com.comphenix.net.bytebuddy.implementation.FieldAccessor.ofField("delegate"))
					.method(
						ElementMatchers.isDeclaredBy(Map.class)
							.and(
								ElementMatchers.not(
									ElementMatchers.named("putIfAbsent")
								)
							)
							.or(
								ElementMatchers.isDeclaredBy(Object.class)
									.and(
										ElementMatchers.named("equals")
											.or(ElementMatchers.named("hashCode"))
											.or(ElementMatchers.named("toString"))
									)
							)
					)
					.intercept(MethodCall.invokeSelf().onField("delegate").withAllArguments())
					.method(ElementMatchers.isDeclaredBy(Map.class).and(ElementMatchers.named("putIfAbsent")))
					.intercept(
						insertHook("hookPutIfAbsent")
							.withField("delegate")
							.withField("entity")
							.withArgument(0)
							.withArgument(1)
					)
					.make()
					.load(EntityTrackerInjector.class.getClassLoader())
					.getLoaded();
				TRACKED_PLAYER_MAP_INJECTOR_CONSTRUCTOR = Accessors.getConstructorAccessor(TRACKED_PLAYER_MAP_INJECTOR_CLASS, Map.class, Entity.class);
				UNWRAP_TRACKED_PLAYER_MAP_INJECTOR = Accessors.getMethodAccessor(TRACKED_PLAYER_MAP_INJECTOR_CLASS, "unwrap");
			}
		}

		private MethodCall.WithoutSpecifiedTarget insertHook(String name) {
			return MethodCall.invoke(
				new TypeDescription.ForLoadedType(EntityTrackerInjector.class)
					.getDeclaredMethods()
					.filter(ElementMatchers.named(name).and(ElementMatchers.isStatic()))
					.getOnly()
				);
		}

		public void hookPut(Object entityTracker) {
			LazyValue<?> entity = LazyValue.of(() -> MinecraftReflection.getBukkitEntity(TRACKER.get(entityTracker)));
			Object trackedPlayers = TRACKED_PLAYERS.get(entityTracker);
			if (!TRACKED_PLAYERS_INJECTOR_CLASS.isInstance(trackedPlayers)) {
				TRACKED_PLAYERS.set(entityTracker, TRACKED_PLAYERS_INJECTOR_CONSTRUCTOR.invoke(trackedPlayers, entity.get()));
			}
			if (TRACKED_PLAYER_MAP == null) {
				return;
			}
			Object trackedPlayerMap = TRACKED_PLAYER_MAP.get(entityTracker);
			if (!TRACKED_PLAYER_MAP_INJECTOR_CLASS.isInstance(trackedPlayerMap)) {
				TRACKED_PLAYER_MAP.set(entityTracker, TRACKED_PLAYER_MAP_INJECTOR_CONSTRUCTOR.invoke(trackedPlayerMap, entity.get()));
			}
		}

		public boolean hookAdd(Set<Object> set, Entity entity, Object nmsPlayer) {
			if (set.add(nmsPlayer)) {
				if (NMS_PLAYER_CLASS.isInstance(nmsPlayer)) {
					Bukkit.getPluginManager().callEvent(new EntityTrackedPlayerAddEvent(entity, (Player) MinecraftReflection.getBukkitEntity(nmsPlayer)));
				}
				return true;
			}
			return false;
		}

		public boolean hookRemove(Set<Object> set, Entity entity, Object nmsPlayer) {
			if (set.remove(nmsPlayer)) {
				if (NMS_PLAYER_CLASS.isInstance(nmsPlayer)) {
					Bukkit.getPluginManager().callEvent(new EntityTrackedPlayerRemoveEvent(entity, (Player) MinecraftReflection.getBukkitEntity(nmsPlayer)));
				}
				return true;
			}
			return false;
		}

		public Iterator<Object> hookIterator(Set<Object> set, Entity entity) {
			StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
			if (ste.getClassName().equals(ENTITY_TRACKER_CLASS.getName()) && !ste.getMethodName().equals("broadcast")) {
				set.forEach(nmsPlayer -> Bukkit.getPluginManager().callEvent(new EntityTrackedPlayerRemoveEvent(entity, (Player)MinecraftReflection.getBukkitEntity(nmsPlayer))));
			}
			return set.iterator();
		}

		public Boolean hookPutIfAbsent(Map<Object, Boolean> map, Entity entity, Object nmsPlayer, Boolean bool) {
			Boolean retVal = map.putIfAbsent(nmsPlayer, bool);
			if (retVal == null) {
				if (NMS_PLAYER_CLASS.isInstance(nmsPlayer)) {
					Bukkit.getPluginManager().callEvent(new EntityTrackedPlayerAddEvent(entity, (Player)MinecraftReflection.getBukkitEntity(nmsPlayer)));
				}
			}
			return retVal;
		}

		public void inject(World world) {
			Object playerChunkMap = getPlayerChunkMap(world);
			Object trackedEntities = TRACKED_ENTITIES.get(playerChunkMap);
			if (!TRACKED_ENTITIES_INJECTOR_CLASS.isInstance(trackedEntities)) {
				TRACKED_ENTITIES.set(playerChunkMap, TRACKED_ENTITIES_INJECTOR_CONSTRUCTOR.invoke(trackedEntities));
			}
		}

		public void remove(World world) {
			Object playerChunkMap = getPlayerChunkMap(world);
			Int2ObjectMap<?> trackedEntities = (Int2ObjectMap<?>)TRACKED_ENTITIES.get(playerChunkMap);
			if (TRACKED_ENTITIES_INJECTOR_CLASS.isInstance(trackedEntities)) {
				TRACKED_ENTITIES.set(playerChunkMap, UNWRAP_TRACKED_ENTITIES_INJECTOR.invoke(trackedEntities));
			}
			trackedEntities.values().forEach(entityTracker -> {
				Object trackedPlayers = TRACKED_PLAYERS.get(entityTracker);
				if (TRACKED_PLAYERS_INJECTOR_CLASS.isInstance(trackedPlayers)) {
					TRACKED_PLAYERS.set(entityTracker, UNWRAP_TRACKED_PLAYERS_INJECTOR.invoke(trackedPlayers));
				}
				if (TRACKED_PLAYER_MAP == null) {
					return;
				}
				Object trackedPlayerMap = TRACKED_PLAYER_MAP.get(entityTracker);
				if (TRACKED_PLAYER_MAP_INJECTOR_CLASS.isInstance(trackedPlayerMap)) {
					TRACKED_PLAYER_MAP.set(entityTracker, UNWRAP_TRACKED_PLAYER_MAP_INJECTOR.invoke(trackedPlayerMap));
				}
			});
		}

	}

}
