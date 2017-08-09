package org.moussel.resynchrosub;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Operation;

public class ResynchroCore {
	static final Logger LOGGER = Logger.getLogger(ResynchroCore.class.getName());

	static final String SUB_SEPARATOR = "&§&" + System.lineSeparator();

	static void addOrMergeToList(AtomicListElement<Diff> difList, Diff toBeAdded) {
		if (!difList.isEmpty() && difList.peekLast().operation.equals(toBeAdded.operation)) {
			// Merge with last element
			difList.peekLast().text += toBeAdded.text;
		} else {
			difList.add(toBeAdded);
		}
	}

	static List<SubtitleMatch> computeMatchSameLanguage(List<Subtitle> srcSubList, List<Subtitle> reSyncSrcSubList) {
		String srcFullText = srcSubList.stream().map(sub -> sub.getTextToCompare())
				.collect(Collectors.joining(SUB_SEPARATOR)) + SUB_SEPARATOR;
		String reSyncSrcFullText = reSyncSrcSubList.stream().map(sub -> sub.getTextToCompare())
				.collect(Collectors.joining(SUB_SEPARATOR)) + SUB_SEPARATOR;

		diff_match_patch dmp = new diff_match_patch();
		LinkedList<Diff> diffList = dmp.diff_main(srcFullText, reSyncSrcFullText);

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(StringUtils.join(diffList.stream().map(dif -> {
				return dif.toString();
			}).collect(Collectors.toList()), System.lineSeparator()));
		}
		List<SubtitleMatch> matchList = new ArrayList<>();
		List<SubtitleMatch> tempMatchList = new ArrayList<>();
		StringBuilder remainingText = new StringBuilder();
		final AtomicListElement<Subtitle> currentSrc = new AtomicListElement<>(srcSubList);
		final AtomicListElement<Subtitle> currentReSyncSrc = new AtomicListElement<>(reSyncSrcSubList);

		final StringBuilder accumulateInsert = new StringBuilder();
		final StringBuilder accumulateDelete = new StringBuilder();

		AtomicListElement<Diff> originalDiffList = new AtomicListElement<>(diffList);
		final AtomicListElement<Diff> processedDiffList = new AtomicListElement<>();
		originalDiffList.forEach(dif -> {
			if (dif.text.contains(SUB_SEPARATOR) && (accumulateInsert.length() > 0 || accumulateDelete.length() > 0)) {
				// Stop accumulate
				String[] splitText = splitAfterLastIndexOf(dif.text, SUB_SEPARATOR);
				switch (dif.operation) {
					case INSERT :
						if (StringUtils.isNotBlank(accumulateInsert)) {
							ResynchroCore.addOrMergeToList(processedDiffList,
									new Diff(Operation.INSERT, accumulateInsert.toString() + " " + splitText[0]));
							accumulateInsert.delete(0, accumulateInsert.length());
						} else {
							ResynchroCore.addOrMergeToList(processedDiffList, new Diff(Operation.INSERT, splitText[0]));
						}
						break;
					case DELETE :
						if (StringUtils.isNotBlank(accumulateDelete)) {
							ResynchroCore.addOrMergeToList(processedDiffList,
									new Diff(Operation.DELETE, accumulateDelete.toString() + " " + splitText[0]));
							accumulateDelete.delete(0, accumulateDelete.length());
						} else {
							ResynchroCore.addOrMergeToList(processedDiffList, new Diff(Operation.DELETE, splitText[0]));
						}
						break;
					default : // EQUAL
						splitText = splitAfterFirstIndexOf(dif.text, SUB_SEPARATOR);
						ResynchroCore.addOrMergeToList(processedDiffList,
								new Diff(Operation.INSERT, accumulateInsert.toString() + " " + splitText[0]));
						ResynchroCore.addOrMergeToList(processedDiffList,
								new Diff(Operation.DELETE, accumulateDelete.toString() + " " + splitText[0]));
						accumulateInsert.delete(0, accumulateInsert.length());
						accumulateDelete.delete(0, accumulateDelete.length());
						break;
				}

				if (StringUtils.isNotBlank(splitText[1])) {
					dif.text = splitText[1];
				} else {
					return; // Continue
				}

			}

			if (dif.text.contains(SUB_SEPARATOR)) {
				if (dif.text.endsWith(SUB_SEPARATOR)) {
					ResynchroCore.addOrMergeToList(processedDiffList, dif);
				} else {
					String[] splitText = splitAfterLastIndexOf(dif.text, SUB_SEPARATOR);
					ResynchroCore.addOrMergeToList(processedDiffList, new Diff(dif.operation, splitText[0]));
					dif.text = splitText[1];
					if (originalDiffList.isFirst()) {
						originalDiffList.reset();
					} else {
						originalDiffList.movePrevious();
					}
					return; // Continue ForEach on the same
				}
			} else {
				if (StringUtils.isNotBlank(dif.text)) {
					if (dif.operation.equals(Operation.INSERT) || dif.operation.equals(Operation.EQUAL)) {
						accumulateInsert.append(" " + dif.text);
					}
					if (dif.operation.equals(Operation.DELETE) || dif.operation.equals(Operation.EQUAL)) {
						accumulateDelete.append(" " + dif.text);
					}
				}
			}
		});

		processedDiffList.reset();
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine("processedDiffList: " + System.lineSeparator()
					+ StringUtils.join(processedDiffList.stream().map(dif -> {
						return dif.toString();
					}).collect(Collectors.toList()), System.lineSeparator()));
		}

		final AtomicListElement<String> unmatched = new AtomicListElement<>();
		processedDiffList.forEach(dif -> {
			String[] multiSubMatch = dif.text.split(SUB_SEPARATOR, -1);
			SubtitleMatch previousMatch = null;
			if (!tempMatchList.isEmpty()) {
				previousMatch = tempMatchList.get(tempMatchList.size() - 1);
				if (previousMatch.isSplit()) {
					multiSubMatch = ArrayUtils.subarray(multiSubMatch, 1, multiSubMatch.length);
				}
			}
			if (unmatched.isEmpty()) {
				// commit matched subs
				if (matchList.addAll(tempMatchList)) {
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.fine("Validated tempMatchList elements: " + System.lineSeparator()
								+ StringUtils.join(tempMatchList.stream().map(match -> {
									return match.toString();
								}).collect(Collectors.toList()), System.lineSeparator()));
					}
				}
				tempMatchList.clear();
				unmatched.reset(multiSubMatch);
			} else {
				unmatched.addAll(multiSubMatch);
			}

			if (unmatched.size() == 1 && StringUtils.isBlank(unmatched.peekNext())) {
				unmatched.clear();
				return; // Continue forEach
			}
			switch (dif.operation) {
				case INSERT :
					for (String subPart : unmatched) {
						if (unmatched.isFirst() && StringUtils.isNotBlank(remainingText.toString())) {
							if (currentReSyncSrc.get().getTextToCompare().startsWith(remainingText.toString())) {
								// Split case
								tempMatchList.add(new SubtitleMatch(currentSrc.getThenNext(),
										new Subtitle[]{currentReSyncSrc.getThenNext(), currentReSyncSrc.getThenNext()},
										MatchingMode.SPLIT_2B_IS_1A));
								remainingText.delete(0, remainingText.length());
								unmatched.moveNext();
								continue;
							}
						}
						if (unmatched.hasNext()) {
							tempMatchList
									.add(new SubtitleMatch(currentReSyncSrc.getThenNext(), MatchingMode.MISSING_A));
						} else if (!StringUtils.isBlank(subPart)) {
							// Last is not empty => Split sub
							remainingText.append(subPart);
						}
					}
					unmatched.resetWithRemainingElements();
					break;
				case DELETE :
					for (String subPart : unmatched) {
						if (StringUtils.isNotBlank(remainingText)) {
							if (StringUtils.isBlank(subPart)) {
								if (unmatched.size() > 1) {
									// Split case
									currentSrc.moveNext();
									tempMatchList.add(new SubtitleMatch(
											new Subtitle[]{currentSrc.peekPrevious(), currentSrc.get()},
											currentReSyncSrc.getThenNext(), MatchingMode.SPLIT_2A_IS_1B));
									remainingText.delete(0, remainingText.length());
								} else {
									continue;
								}
							}
						}
						if (unmatched.hasNext()) {
							tempMatchList.add(new SubtitleMatch(currentSrc.getThenNext(), MatchingMode.MISSING_B));
						} else if (!StringUtils.isBlank(subPart)) {
							// Last is not empty => Split sub
							remainingText.append(subPart);
						}
					}
					unmatched.resetWithRemainingElements();
					break;

				default : // EQUALS
					for (String subPart : unmatched) {
						if (StringUtils.isNotBlank(remainingText)) {
							if (StringUtils.isBlank(subPart)) {
								if (unmatched.size() > 1) {
									// Split case
									currentSrc.moveNext();
									tempMatchList.add(new SubtitleMatch(
											new Subtitle[]{currentSrc.peekPrevious(), currentSrc.get()},
											currentReSyncSrc.getThenNext(), MatchingMode.SPLIT_2A_IS_1B));
									remainingText.delete(0, remainingText.length());
								} else {
									continue;
								}
							}
						}
						if (unmatched.hasNext()) {
							tempMatchList.add(new SubtitleMatch(currentSrc.getThenNext(),
									currentReSyncSrc.getThenNext(), MatchingMode.PERFECT_MATCH));
						} else if (!StringUtils.isBlank(subPart)) {
							// Last is not empty => Split sub
							remainingText.append(subPart);
						}
					}
					unmatched.resetWithRemainingElements();
					break;
			}
		});
		// commit matched subs
		if (matchList.addAll(tempMatchList)) {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine("Validated tempMatchList elements (end): " + System.lineSeparator()
						+ StringUtils.join(tempMatchList.stream().map(match -> {
							return match.toString();
						}).collect(Collectors.toList()), System.lineSeparator()));
			}
		}
		tempMatchList.clear();

		AtomicListElement<SubtitleMatch> theMatchList = new AtomicListElement<>();
		theMatchList.addAll(matchList);
		// Missing A, Missing B =>? Approx Match ?
		theMatchList.forEach(match -> {
			if (match.mode.equals(MatchingMode.MISSING_A) && theMatchList.hasNext() && !theMatchList.isFirst()) {
				SubtitleMatch peekPrevious = theMatchList.peekPrevious();
				SubtitleMatch peekNext = theMatchList.peekNext();
				if (!peekPrevious.mode.equals(MatchingMode.MISSING_A) && peekNext.mode.equals(MatchingMode.MISSING_B)) {
					match.mode = MatchingMode.APPROX_MATCH;
					match.subAList.addAll(peekNext.subAList);
					theMatchList.removeNext();
				} else if (peekPrevious.isMatch()
						&& peekPrevious.getLastA().getTextToCompare().endsWith(match.getFirstB().getTextToCompare())) {
					peekPrevious.mode = MatchingMode.SPLIT_2B_IS_1A;
					peekPrevious.subBList.addAll(match.subBList);
					theMatchList.remove();
				}
			} else if (match.mode.equals(MatchingMode.MISSING_B) && theMatchList.hasNext() && !theMatchList.isFirst()) {
				SubtitleMatch peekPrevious = theMatchList.peekPrevious();
				SubtitleMatch peekNext = theMatchList.peekNext();
				if (!peekPrevious.mode.equals(MatchingMode.MISSING_B) && peekNext.mode.equals(MatchingMode.MISSING_A)) {
					match.mode = MatchingMode.APPROX_MATCH;
					match.subBList.addAll(peekNext.subBList);
					theMatchList.removeNext();
				} else if (peekPrevious.isMatch()
						&& peekPrevious.getLastB().getTextToCompare().endsWith(match.getFirstA().getTextToCompare())) {
					peekPrevious.mode = MatchingMode.SPLIT_2A_IS_1B;
					peekPrevious.subAList.addAll(match.subAList);
					theMatchList.remove();
				}
			}
		});

		return theMatchList.underlyingList;
	}

	static List<SubtitleMatch> computeMatchSameVersion(List<Subtitle> srcSubList, List<Subtitle> toReSyncSubList) {

		List<SubtitleMatch> resultList = new ArrayList<>();
		int indexA = 0, indexB = 0;

		while (indexA < srcSubList.size() && indexB < toReSyncSubList.size()) {
			Subtitle currentA = srcSubList.get(indexA);
			Subtitle currentB = toReSyncSubList.get(indexB);
			if (ResynchroTimeHelper.timeEqualsOrCloseEnough(currentA.getStartTime(), currentB.getStartTime())
					&& ResynchroTimeHelper.timeEqualsOrCloseEnough(currentA.getEndTime(), currentB.getEndTime())) {
				// Perfect match
				resultList.add(new SubtitleMatch(currentA, currentB, MatchingMode.PERFECT_MATCH));
				indexA++;
				indexB++;
				continue;
			}
			if (currentA.getEndTime().isBefore(currentB.getStartTime())) {
				// A completely before B -> Missing B
				resultList.add(new SubtitleMatch(currentA, MatchingMode.MISSING_B));
				indexA++;
				continue;
			}
			if (currentB.getEndTime().isBefore(currentA.getStartTime())) {
				// B completely before A -> Missing A
				resultList.add(new SubtitleMatch(currentB, MatchingMode.MISSING_A));
				indexB++;
				continue;
			}

			Subtitle nextA = null, nextB = null;
			if (indexA + 1 < srcSubList.size()) {
				nextA = srcSubList.get(indexA + 1);
			}
			if (indexB + 1 < toReSyncSubList.size()) {
				nextB = toReSyncSubList.get(indexB + 1);
			}
			if (ResynchroTimeHelper.timeEqualsOrCloseEnough(currentA.getStartTime(), currentB.getStartTime())
					&& nextB != null
					&& ResynchroTimeHelper.timeEqualsOrCloseEnough(currentA.getEndTime(), nextB.getEndTime())) {
				// Perfect Split
				resultList
						.add(new SubtitleMatch(currentA, new Subtitle[]{currentB, nextB}, MatchingMode.SPLIT_2B_IS_1A));
				indexA++;
				indexB += 2;
				continue;
			}
			if (ResynchroTimeHelper.timeEqualsOrCloseEnough(currentB.getStartTime(), currentA.getStartTime())
					&& nextA != null
					&& ResynchroTimeHelper.timeEqualsOrCloseEnough(currentB.getEndTime(), nextA.getEndTime())) {
				// Perfect Split
				resultList
						.add(new SubtitleMatch(new Subtitle[]{currentA, nextA}, currentB, MatchingMode.SPLIT_2A_IS_1B));
				indexA += 2;
				indexB++;
				continue;
			}
			if (ResynchroTimeHelper.timeBeforeOrCloseEnough(currentA.getStartTime(), currentB.getStartTime())
					&& nextB != null
					&& ResynchroTimeHelper.timeAfterOrCloseEnough(currentA.getEndTime(), nextB.getEndTime())) {
				// Approx Split
				resultList
						.add(new SubtitleMatch(currentA, new Subtitle[]{currentB, nextB}, MatchingMode.SPLIT_2B_IS_1A));
				indexA++;
				indexB += 2;
				continue;
			}
			if (ResynchroTimeHelper.timeBeforeOrCloseEnough(currentB.getStartTime(), currentA.getStartTime())
					&& nextA != null
					&& ResynchroTimeHelper.timeAfterOrCloseEnough(currentB.getEndTime(), nextA.getEndTime())) {
				// Approx Split
				resultList
						.add(new SubtitleMatch(new Subtitle[]{currentA, nextA}, currentB, MatchingMode.SPLIT_2A_IS_1B));
				indexA += 2;
				indexB++;
				continue;
			}

			resultList.add(new SubtitleMatch(currentA, currentB, MatchingMode.APPROX_MATCH));
			indexA++;
			indexB++;
		}
		// Process remaining

		if (indexA < srcSubList.size()) {
			while (indexA < srcSubList.size()) {
				resultList.add(new SubtitleMatch(srcSubList.get(indexA++), MatchingMode.MISSING_B));
			}
		}
		if (indexB < toReSyncSubList.size()) {
			while (indexB < toReSyncSubList.size()) {
				resultList.add(new SubtitleMatch(toReSyncSubList.get(indexB++), MatchingMode.MISSING_A));
			}
		}

		List<SubtitleMatch> matchListPass2 = new ArrayList<>();
		SubtitleMatch prevMatch = null;
		for (SubtitleMatch match : resultList) {
			System.out.println(match.toString());
			switch (match.mode) {
				case MISSING_B :
					try {
						Subtitle prevB = prevMatch.getLastB();
						Subtitle curA = match.getFirstA();
						if (prevB != null && ResynchroTimeHelper.timeAfterOrCloseEnough(prevB.getEndTime(),
								curA.getStartTime())) {
							prevMatch.subAList.add(curA);
							prevMatch.mode = MatchingMode.SPLIT_2A_IS_1B;
							break;
						}
					} catch (Exception e) { // Silent Catch

					}
					matchListPass2.add(match);
					prevMatch = match;
					break;
				case MISSING_A :
					try {
						Subtitle prevA = prevMatch.getLastA();
						Subtitle curB = match.getFirstB();
						if (prevA != null && ResynchroTimeHelper.timeAfterOrCloseEnough(prevA.getEndTime(),
								curB.getStartTime())) {
							prevMatch.subBList.add(curB);
							prevMatch.mode = MatchingMode.SPLIT_2B_IS_1A;
							break;
						}
					} catch (Exception e) {
						// Silent Catch
					}
					matchListPass2.add(match);
					prevMatch = match;
					break;

				default :
					matchListPass2.add(match);
					prevMatch = match;
					break;
			}
		}

		return matchListPass2;
	}

	static void createReSyncFile(File sourceFile, File translatedFile, File syncedFile, File translatedSyncFile)
			throws IOException {
		SubtitleFile srcSubFile = new SubtitleFile(sourceFile);
		List<Subtitle> srcSubList = srcSubFile.parseAndGetSubList();
		LOGGER.fine("Nb subs for bad version-bad language: " + srcSubList.size());
		SubtitleFile toReSyncSubFile = new SubtitleFile(translatedFile);
		List<Subtitle> toResyncSubList = toReSyncSubFile.parseAndGetSubList();
		LOGGER.fine("Nb subs for bad version-good language: " + toResyncSubList.size());
		SubtitleFile resyncSrcSubFile = new SubtitleFile(syncedFile);
		List<Subtitle> resyncSrcSubList = resyncSrcSubFile.parseAndGetSubList();
		LOGGER.fine("Nb subs for good version-bad language: " + resyncSrcSubList.size());

		List<SubtitleMatch> resyncMatchList = computeMatchSameLanguage(srcSubList, resyncSrcSubList);

		SubtitleMatch.showStats(resyncMatchList);

		List<SubtitleMatch> translatedMatchList = computeMatchSameVersion(srcSubList, toResyncSubList);
		SubtitleMatch.showStats(translatedMatchList);

		List<Subtitle> resyncTranslation = reSync(toResyncSubList, resyncMatchList, translatedMatchList);
		SubtitleFile translatedSyncSubFile = new SubtitleFile(translatedSyncFile);
		translatedSyncSubFile.setSubList(resyncTranslation);
		translatedSyncSubFile.writeFile();
	}

	static List<Subtitle> reSync(List<Subtitle> toResyncSubList, List<SubtitleMatch> resyncMatchList,
			List<SubtitleMatch> translatedMatchList) throws IOException {
		Subtitle[] resyncTranslation = new Subtitle[toResyncSubList.size()];

		AtomicListElement<SubtitleMatch> syncMatchList = new AtomicListElement<>(resyncMatchList);
		AtomicListElement<SubtitleMatch> transMatchList = new AtomicListElement<>(translatedMatchList);

		transMatchList.forEach(tMatch -> {
			if (tMatch.isMatch()) {
				final Integer srcNum = tMatch.subAList.get(0).getSubNumber();
				if (syncMatchList.moveUntil(SubtitleMatch.sameSubANumPredicate(srcNum))) {
					SubtitleMatch syncMatch = syncMatchList.get();
					if (syncMatch.isMatch() || MatchingMode.SPLIT_2B_IS_1A.equals(syncMatch.mode)) {
						Integer translatedNum = tMatch.subBList.get(0).getSubNumber();
						resyncTranslation[translatedNum - 1] = tMatch.subBList.get(0).resyncTo(syncMatch.subBList);
					} else if (MatchingMode.SPLIT_2A_IS_1B.equals(syncMatch.mode)
							&& transMatchList.peekNext().isMatch()) {
						Integer translatedNum = tMatch.subBList.get(0).getSubNumber();
						List<Subtitle> transToSyncList = new ArrayList<>();
						transToSyncList.add(tMatch.subBList.get(0));
						transToSyncList.add(transMatchList.next().subBList.get(0));
						List<Subtitle> currentResyncedTransList = syncMatch.subBList.get(0)
								.resyncSplitFrom(transToSyncList);
						resyncTranslation[translatedNum - 1] = currentResyncedTransList.get(0);
						resyncTranslation[translatedNum] = currentResyncedTransList.get(1);
					}
				}
			}
		});
		syncMatchList.reset();
		translatedMatchList.stream().filter(tMatch -> {
			return Arrays.asList(MatchingMode.SPLIT_2B_IS_1A).contains(tMatch.mode);
		}).forEach(tMatch -> {
			final Integer srcNum = tMatch.subAList.get(0).getSubNumber();
			if (syncMatchList.moveUntil(SubtitleMatch.sameSubANumPredicate(srcNum))) {
				SubtitleMatch syncMatch = syncMatchList.get();
				if (Arrays.asList(MatchingMode.PERFECT_MATCH, MatchingMode.APPROX_MATCH).contains(syncMatch.mode)) {
					List<Subtitle> tSyncList = syncMatch.subBList.get(0).resyncSplitFrom(tMatch.subBList);
					tSyncList.forEach(tsyncSub -> {
						resyncTranslation[tsyncSub.getSubNumber() - 1] = tsyncSub;
					});
				}

			}
		});
		syncMatchList.reset();
		translatedMatchList.stream().filter(tMatch -> {
			return Arrays.asList(MatchingMode.SPLIT_2A_IS_1B).contains(tMatch.mode);
		}).forEach(tMatch -> {
			final List<Subtitle> syncedSrcList = new ArrayList<>();
			for (Subtitle src : tMatch.subAList) {
				final Integer srcNum = src.getSubNumber();
				if (syncMatchList.moveUntil(SubtitleMatch.sameSubANumPredicate(srcNum))) {
					SubtitleMatch syncMatch = syncMatchList.get();
					if (Arrays.asList(MatchingMode.PERFECT_MATCH, MatchingMode.APPROX_MATCH).contains(syncMatch.mode)) {
						syncedSrcList.addAll(syncMatch.subBList);
					} else { // hard case
						// TODO: figure out what to do here
					}
				}
			}
			resyncTranslation[tMatch.subBList.get(0).getSubNumber() - 1] = tMatch.subBList.get(0)
					.resyncTo(syncedSrcList);
		});
		long syncedSoFar = Arrays.asList(resyncTranslation).stream().filter(sub -> {
			return sub != null;
		}).count();
		LOGGER.fine("Total synced: " + syncedSoFar);

		transMatchList.reset();
		syncMatchList.reset();
		for (int translatedSubNum = 1; translatedSubNum < resyncTranslation.length - 1; translatedSubNum++) {
			if (resyncTranslation[translatedSubNum] == null && resyncTranslation[translatedSubNum - 1] != null
					&& resyncTranslation[translatedSubNum + 1] != null) {

				final Integer transNum = new Integer(translatedSubNum + 1);
				if (transMatchList.moveUntil(SubtitleMatch.sameSubBNumPredicate(transNum))) {
					SubtitleMatch transMatch = transMatchList.get();
					if (Arrays.asList(MatchingMode.PERFECT_MATCH, MatchingMode.APPROX_MATCH)
							.contains(transMatch.mode)) {
						final Integer srcNum = new Integer(transMatch.subAList.get(0).getSubNumber());

						if (syncMatchList.moveUntil(SubtitleMatch.sameSubANumPredicate(srcNum))) {
							SubtitleMatch syncMatch = syncMatchList.get();
							if (Arrays.asList(MatchingMode.PERFECT_MATCH, MatchingMode.APPROX_MATCH,
									MatchingMode.SPLIT_2B_IS_1A).contains(syncMatch.mode)) {
								resyncTranslation[translatedSubNum] = transMatch.subBList.get(0)
										.resyncTo(syncMatch.subBList);
							} else if (syncMatch.mode.equals(MatchingMode.MISSING_B)) {

							}
						}
					}
				}
			}
		}

		syncedSoFar = Arrays.asList(resyncTranslation).stream().filter(sub -> {
			return sub != null;
		}).count();
		LOGGER.info("Total synced: " + syncedSoFar + "/" + resyncTranslation.length);

		return Arrays.asList(resyncTranslation);
	}

	static String[] splitAfterFirstIndexOf(String origin, String separator) {
		int splitIndex = origin.indexOf(separator) + separator.length();
		String before = origin.substring(0, splitIndex);
		String leftOver = origin.substring(splitIndex, origin.length());
		return new String[]{before, leftOver};
	}

	static String[] splitAfterLastIndexOf(String origin, String separator) {
		int splitIndex = origin.lastIndexOf(separator) + separator.length();
		String before = origin.substring(0, splitIndex);
		String leftOver = origin.substring(splitIndex, origin.length());
		return new String[]{before, leftOver};
	}

}
