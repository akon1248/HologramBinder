package com.akon.hologrambinder.util;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.function.Supplier;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class LazyValue<V> {

	private Optional<V> cached;
	private final Supplier<V> initializer;

	public static <T> LazyValue<T> of(Supplier<T> initializer) {
		return new LazyValue<>(initializer);
	}

	public synchronized V get() {
		if (cached == null) {
			this.cached = Optional.ofNullable(this.initializer.get());
		}
		return this.cached.orElse(null);
	}

}
