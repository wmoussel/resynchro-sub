package org.moussel.resynchrosub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

public class AtomicListElement<T> implements Iterable<T>, Iterator<T> {

	AtomicInteger index = new AtomicInteger(-1); // So that first call to next
													// gets the first element
	List<T> underlyingList;

	public AtomicListElement() {
		this.underlyingList = new ArrayList<T>();
	}

	public AtomicListElement(List<T> underlyingList) {
		this.underlyingList = underlyingList;
	}

	public AtomicListElement(T[] underlyingArray) {
		this.underlyingList = new ArrayList<T>();
		this.underlyingList.addAll(Arrays.asList(underlyingArray));
	}

	public void add(T element) {
		underlyingList.add(element);

	}

	public boolean addAll(List<T> listToAdd) {
		return underlyingList.addAll(listToAdd);
	}

	public boolean addAll(T[] arrayToAdd) {
		return underlyingList.addAll(Arrays.asList(arrayToAdd));
	}

	public void clear() {
		this.underlyingList = new ArrayList<T>();
		this.reset();
	}

	public T get() {
		if (index.intValue() == -1) {
			return moveNext();
		} else {
			try {
				return underlyingList.get(index.intValue());
			} catch (IndexOutOfBoundsException oob) {
				return null;
			}
		}
	}

	public List<T> getList() {
		return underlyingList;
	}

	public List<T> getRemainingElements() {
		if (hasNext()) {
			return underlyingList.subList(index.intValue() + 1, underlyingList.size());
		} else {
			return new ArrayList<T>(0);
		}
	}

	public T getThenNext() {
		T returnValue = get();
		if (returnValue == null) {
			return null;
		}
		index.incrementAndGet();
		return returnValue;
	}

	@Override
	public boolean hasNext() {
		return underlyingList != null && index.intValue() + 1 < underlyingList.size();
	}

	public boolean isEmpty() {
		return underlyingList == null || underlyingList.isEmpty();
	}

	public boolean isFirst() {
		return index.intValue() == 0;
	}
	public boolean isLast() {
		return index.intValue() == underlyingList.size();
	}
	@Override
	public Iterator<T> iterator() {
		return this;
	}

	public T moveNext() {
		try {
			return underlyingList.get(index.incrementAndGet());
		} catch (IndexOutOfBoundsException oob) {
			index.decrementAndGet();
			return null;
		}
	}

	public T movePrevious() {
		try {
			return underlyingList.get(index.decrementAndGet());
		} catch (IndexOutOfBoundsException oob) {
			index.incrementAndGet();
			return null;
		}
	}

	public boolean moveUntil(Predicate<T> predicateToStop) {
		return moveUntil(predicateToStop, false);
	}
	public boolean moveUntil(Predicate<T> predicateToStop, boolean loop) {
		int startIndex = index.get();
		int size = underlyingList.size();
		int stopAt = loop ? startIndex - 1 : size - 1;
		for (int newIdx = ((startIndex == -1) ? 0 : startIndex); newIdx != stopAt; newIdx++) {
			if (loop && newIdx > size) {
				newIdx = 0;
			}
			if (predicateToStop.test(underlyingList.get(newIdx))) {
				index.set(newIdx);
				return true;
			}
		}
		return false;

	}
	@Override
	public T next() {
		return moveNext();
	}

	public T peekLast() {
		if (underlyingList == null) {
			return null;
		} else {
			return underlyingList.get(underlyingList.size() - 1);
		}
	}

	public T peekNext() {
		try {
			return underlyingList.get(index.intValue() + 1);
		} catch (IndexOutOfBoundsException oob) {
			return null;
		}
	}

	public T peekPrevious() {
		try {
			return underlyingList.get(index.intValue() - 1);
		} catch (IndexOutOfBoundsException oob) {
			return null;
		}
	}

	@Override
	public void remove() {
		this.underlyingList.remove(index.intValue());
		index.decrementAndGet();
	}

	public void removeNext() {
		if (!isLast()) {
			this.underlyingList.remove(index.intValue() + 1);
		}
	}

	public void reset() {
		index.set(-1);
	}

	public void reset(List<T> underlyingList) {
		this.underlyingList = underlyingList;
		reset();
	}

	public void reset(T[] underlyingArray) {
		this.underlyingList = new ArrayList<T>();
		this.underlyingList.addAll(Arrays.asList(underlyingArray));
		reset();
	}

	public void resetWithRemainingElements() {
		reset(getRemainingElements());
	}

	public int size() {
		return underlyingList == null ? 0 : underlyingList.size();
	}

	public Stream<T> stream() {
		return underlyingList.stream();
	}

	public String toStringDetail() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < underlyingList.size(); i++) {
			sb.append(StringUtils.chomp(underlyingList.get(i).toString()));
			if (i == index.intValue()) {
				sb.append(" >>> Cursor is here (" + i + ")");
			}
			sb.append(System.lineSeparator());
		}
		return StringUtils.chomp(sb.toString());
	}
}
