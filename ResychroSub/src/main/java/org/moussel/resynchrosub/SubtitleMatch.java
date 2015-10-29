package org.moussel.resynchrosub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SubtitleMatch {
	public static Predicate<SubtitleMatch> sameSubANumPredicate(final Integer srcNum) {
		return m -> {
			return m.subAList.stream().anyMatch(s -> {
				return s.getSubNumber().equals(srcNum);
			});
		};
	}

	public static Predicate<SubtitleMatch> sameSubBNumPredicate(final Integer srcNum) {
		return m -> {
			return m.subBList.stream().anyMatch(s -> {
				return s.getSubNumber().equals(srcNum);
			});
		};
	}

	public static void showStats(List<SubtitleMatch> matchList) {
		Map<MatchingMode, AtomicInteger> stats = new LinkedHashMap<>();
		for (MatchingMode val : MatchingMode.values()) {
			stats.put(val, new AtomicInteger(0));
		}
		matchList.forEach(match -> {
			stats.get(match.getMode()).incrementAndGet();
		});
		stats.forEach((matchMode, statNum) -> {
			System.out.println(matchMode.toString() + ": " + statNum.get());
		});
	}

	MatchingMode mode;
	List<Subtitle> subAList = new ArrayList<>();

	List<Subtitle> subBList = new ArrayList<>();

	public SubtitleMatch(Subtitle sub, MatchingMode subMatch) {
		if (MatchingMode.MISSING_A.equals(subMatch)) {
			this.subBList.add(sub);
		} else if (MatchingMode.MISSING_B.equals(subMatch)) {
			this.subAList.add(sub);
		} else {
			throw new RuntimeException(this.getClass().toGenericString()
					+ " Constructor used with 1 sub and matchingMode=" + subMatch);
		}

		this.mode = subMatch;
	}

	public SubtitleMatch(Subtitle subA, Subtitle subB, MatchingMode subMatch) {
		if (subA != null) {
			this.subAList.add(subA);
		}
		if (subB != null) {
			this.subBList.add(subB);
		}
		this.mode = subMatch;
	}

	public SubtitleMatch(Subtitle subA, Subtitle[] subsB, MatchingMode subMatch) {
		if (subA != null) {
			this.subAList.add(subA);
		}
		if (subsB != null) {
			this.subBList.addAll(Arrays.asList(subsB));
		}
		this.mode = subMatch;
	}

	public SubtitleMatch(Subtitle[] subsA, Subtitle subB, MatchingMode subMatch) {
		if (subsA != null) {
			this.subAList.addAll(Arrays.asList(subsA));
		}
		if (subB != null) {
			this.subBList.add(subB);
		}
		this.mode = subMatch;
	}

	private <T> T getFirst(List<T> list) {
		if (list.isEmpty()) {
			return null;
		}
		return list.get(0);
	}

	public Subtitle getFirstA() {
		return getFirst(subAList);
	}

	public Subtitle getFirstB() {
		return getFirst(subBList);
	}

	private <T> T getLast(List<T> list) {
		if (list.isEmpty()) {
			return null;
		}
		return list.get(list.size() - 1);
	}

	public Subtitle getLastA() {
		return getLast(subAList);
	}

	public Subtitle getLastB() {
		return getLast(subBList);
	}

	public MatchingMode getMode() {
		return mode;
	}

	public List<Subtitle> getSubAList() {
		return subAList;
	}

	public List<Subtitle> getSubBList() {
		return subBList;
	}

	public boolean isMatch() {
		return MatchingMode.PERFECT_MATCH.equals(mode) || MatchingMode.APPROX_MATCH.equals(mode);
	}
	public boolean isMismatch() {
		return MatchingMode.MISSING_A.equals(mode) || MatchingMode.MISSING_B.equals(mode);
	}
	public boolean isSplit() {
		return MatchingMode.SPLIT_2A_IS_1B.equals(mode) || MatchingMode.SPLIT_2B_IS_1A.equals(mode);
	}

	public void setMode(MatchingMode mode) {
		this.mode = mode;
	}

	public void setSubAList(List<Subtitle> subAList) {
		this.subAList = subAList;
	}

	public void setSubBList(List<Subtitle> subBList) {
		this.subBList = subBList;
	}

	@Override
	public String toString() {
		return mode.toString() + ":\nA: "
				+ subAList.stream().map(Subtitle::lineString).collect(Collectors.joining("\nA: ")) + "\nB: "
				+ subBList.stream().map(Subtitle::lineString).collect(Collectors.joining("\nB: "));

	}
}
