package com.maxxlive.jadepaperbridge.provider.universal;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.google.common.math.IntMath;
import com.maxxlive.jadepaperbridge.accessor.Accessor;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Port of {@code snownee.jade.addon.universal.ItemIterator}, limited to vanilla containers.
 */
public abstract class ItemIterator<T> {
	public static final AtomicLong version = new AtomicLong();
	protected final Function<Accessor, @Nullable T> containerFinder;
	protected final int fromIndex;
	protected boolean finished;
	protected int currentIndex;

	protected ItemIterator(Function<Accessor, @Nullable T> containerFinder, int fromIndex) {
		this.containerFinder = containerFinder;
		this.currentIndex = this.fromIndex = fromIndex;
	}

	public @Nullable T find(Accessor accessor) {
		return containerFinder.apply(accessor);
	}

	public final boolean isFinished() {
		return finished;
	}

	public long getVersion(T container) {
		return version.getAndIncrement();
	}

	public abstract Stream<ItemStack> populate(T container, int amount);

	public void reset() {
		currentIndex = fromIndex;
		finished = false;
	}

	public void afterPopulate(int count) {
		currentIndex += count;
		if (count == 0 || currentIndex >= 10000) {
			finished = true;
		}
	}

	public float getCollectingProgress() {
		return Float.NaN;
	}

	public static class ContainerItemIterator extends ItemIterator<Container> {
		protected float progress;

		public ContainerItemIterator(Function<Accessor, @Nullable Container> containerFinder, int fromIndex) {
			super(containerFinder, fromIndex);
		}

		@Override
		public Stream<ItemStack> populate(Container container, int amount) {
			int slotCount = container.getContainerSize();
			int toIndex = IntMath.saturatedAdd(currentIndex, amount);
			if (toIndex >= slotCount) {
				toIndex = slotCount;
				finished = true;
				progress = 1;
			} else {
				progress = (float) (currentIndex - fromIndex) / (slotCount - fromIndex);
			}
			return IntStream.range(currentIndex, toIndex).mapToObj(container::getItem);
		}

		@Override
		public float getCollectingProgress() {
			return progress;
		}
	}
}
