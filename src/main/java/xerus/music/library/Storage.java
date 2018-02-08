package xerus.music.library;

import java.util.*;
import java.util.function.Consumer;

/**
 * An ArrayList implementation that will adjust it's size automatically and never throws an
 * {@link ArrayIndexOutOfBoundsException} <br>
 * No remove operations supported
 */
public class Storage<E> extends ArrayList<E> {
	
	/** returned as alternate value in {@link #getOrDefault(int)} */
	private E defaultVal;
	
	public Storage() {
		this(null);
	}
	
	public Storage(E defaultVal) {
		super(256);
		this.defaultVal = defaultVal;
	}
	
	/** the index of the last non-null item */
	private int size;
	private int trueSize;
	
	/**
	 * replaces the specified element if it already exists<br>
	 * if this Storage is not yet big enough, it will get expanded by adding nulls
	 * @param element if null, this instantly returns null
	 */
	@Override
	public E set(int index, E element) {
		if (element == null)
			return null;
		super.ensureCapacity(index + 1);
		for (int s = index - super.size(); s >= 0; s--) {
			super.add(null);
		}
		if (super.set(index, element) == null)
			trueSize++;
		updateSize(true);
		return null;
	}
	
	/** shifts nothing, redirects to {@link #set(int, E)} */
	@Override
	public void add(int index, E element) {
		set(index, element);
	}
	
	@Override
	public boolean add(E e) {
		trueSize++;
		return updateSize(super.add(e));
	}
	
	@Override
	public boolean addAll(Collection<? extends E> c) {
		trueSize += c.size();
		return updateSize(super.addAll(c));
	}
	
	private boolean updateSize(boolean update) {
		if (update) {
			size = super.size();
			while (size > 0 && get(size - 1) == null)
				size--;
			return true;
		}
		return false;
	}
	
	@Override
	public void clear() {
		super.clear();
		size = 0;
		trueSize = 0;
	}
	
	@Override
	public E get(int index) {
		if (size > index)
			return super.get(index);
		return null;
	}
	
	public Optional<E> getOptional(int index) {
		if (size > index)
			return Optional.ofNullable(super.get(index));
		return Optional.empty();
	}
	
	public E getOrDefault(int index) {
		if (size > index)
			return super.get(index);
		return defaultVal;
	}
	
	public boolean has(int index) {
		return size > index && super.get(index) != null;
	}
	
	@Override
	public int size() {
		return size;
	}
	
	/** reports how many elements are actually in this storage */
	public int getTrueSize() {
		return trueSize;
	}
	
	/** returns an iterator over this collection that excludes null elements and does not support removal */
	@Override
	public Iterator<E> iterator() {
		return new StorageItr();
	}
	
	/** returns the iterator from the enclosed ArrayList */
	public Iterable<E> fullIterator() {
		return super::iterator;
	}
	
	/**
	 * modified version of {@link ArrayList.Itr}, that skips null elements<br>
	 * does not support removal
	 */
	private class StorageItr implements Iterator<E> {
		int cursor; // index of next element to return
		int expectedModCount = modCount;
		final int size;
		
		public StorageItr() {
			size = size();
		}
		
		@Override
		public boolean hasNext() {
			return advance();
		}
		
		@Override
		public E next() {
			checkForComodification();
			if (!advance())
				throw new NoSuchElementException();
			advanced = false;
			return get(cursor);
		}
		
		private boolean advanced;
		
		private boolean advance() {
			if (advanced)
				return true;
			cursor++;
			while (get(cursor) == null) {
				cursor++;
				if (cursor >= size)
					return false;
			}
			advanced = true;
			return true;
		}
		
		@Override
		public void remove() {
			throw new UnsupportedOperationException("This is a Storage Iterator!");
		}
		
		@Override
		public void forEachRemaining(Consumer<? super E> consumer) {
			Objects.requireNonNull(consumer);
			int i = cursor;
			if (i >= size)
				return;
			while (i != size && modCount == expectedModCount) {
				E element = get(i++);
				if (element != null)
					consumer.accept(element);
			}
			// update once at end of iteration to reduce heap write traffic
			cursor = i;
			checkForComodification();
		}
		
		final void checkForComodification() {
			if (modCount != expectedModCount)
				throw new ConcurrentModificationException();
		}
	}
	
	@Override
	public E remove(int index) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}
	
}
