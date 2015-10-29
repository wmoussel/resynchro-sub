package org.moussel.resynchrosub;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Operation;

public class SubtitleFile {
	static final Scanner inputScanner = new Scanner(System.in);
	static final Logger LOGGER = Logger.getLogger(SubtitleFile.class.getName());
	private static final String SRT_SUB_SEP = "\r\n";

	private static final String SUB_SEPARATOR = "&§&" + System.lineSeparator();

	private static String workFolderName = "/Users/wandrillemoussel/Downloads/";

	private static void addOrMergeToList(AtomicListElement<Diff> difList, Diff toBeAdded) {
		if (!difList.isEmpty() && difList.peekLast().operation.equals(toBeAdded.operation)) {
			// Merge with last element
			difList.peekLast().text += toBeAdded.text;
		} else {
			difList.add(toBeAdded);
		}
	}

	private static List<SubtitleMatch> computeMatchSameLanguage(List<Subtitle> srcSubList,
			List<Subtitle> reSyncSrcSubList) {
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

		AtomicListElement<Diff> originalDiffList = new AtomicListElement<Diff>(diffList);
		final AtomicListElement<Diff> processedDiffList = new AtomicListElement<Diff>();
		originalDiffList.forEach(dif -> {
			if (dif.text.contains(SUB_SEPARATOR) && (accumulateInsert.length() > 0 || accumulateDelete.length() > 0)) {
				// Stop accumulate
				String[] splitText = splitAfterLastIndexOf(dif.text, SUB_SEPARATOR);
				switch (dif.operation) {
					case INSERT :
						if (StringUtils.isNotBlank(accumulateInsert)) {
							addOrMergeToList(processedDiffList,
									new Diff(Operation.INSERT, accumulateInsert.toString() + " " + splitText[0]));
							accumulateInsert.delete(0, accumulateInsert.length());
						} else {
							addOrMergeToList(processedDiffList, new Diff(Operation.INSERT, splitText[0]));
						}
						break;
					case DELETE :
						if (StringUtils.isNotBlank(accumulateDelete)) {
							addOrMergeToList(processedDiffList,
									new Diff(Operation.DELETE, accumulateDelete.toString() + " " + splitText[0]));
							accumulateDelete.delete(0, accumulateDelete.length());
						} else {
							addOrMergeToList(processedDiffList, new Diff(Operation.DELETE, splitText[0]));
						}
						break;
					default : // EQUAL
						splitText = splitAfterFirstIndexOf(dif.text, SUB_SEPARATOR);
						addOrMergeToList(processedDiffList,
								new Diff(Operation.INSERT, accumulateInsert.toString() + " " + splitText[0]));
						addOrMergeToList(processedDiffList,
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
					addOrMergeToList(processedDiffList, dif);
				} else {
					String[] splitText = splitAfterLastIndexOf(dif.text, SUB_SEPARATOR);
					addOrMergeToList(processedDiffList, new Diff(dif.operation, splitText[0]));
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

		final AtomicListElement<String> unmatched = new AtomicListElement<String>();
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
				} else
					if (peekPrevious.isMatch() && peekPrevious.getLastA().getTextToCompare()
							.endsWith(match.getFirstB().getTextToCompare())) {
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
				} else
					if (peekPrevious.isMatch() && peekPrevious.getLastB().getTextToCompare()
							.endsWith(match.getFirstA().getTextToCompare())) {
					peekPrevious.mode = MatchingMode.SPLIT_2A_IS_1B;
					peekPrevious.subAList.addAll(match.subAList);
					theMatchList.remove();
				}
			}
		});

		return theMatchList.underlyingList;
	}

	private static List<SubtitleMatch> computeMatchSameVersion(List<Subtitle> srcSubList,
			List<Subtitle> toReSyncSubList) {

		List<SubtitleMatch> resultList = new ArrayList<SubtitleMatch>();
		int indexA = 0, indexB = 0;

		while (indexA < srcSubList.size() && indexB < toReSyncSubList.size()) {
			Subtitle currentA = srcSubList.get(indexA);
			Subtitle currentB = toReSyncSubList.get(indexB);
			if (timeEqualsOrCloseEnough(currentA.getStartTime(), currentB.getStartTime())
					&& timeEqualsOrCloseEnough(currentA.getEndTime(), currentB.getEndTime())) {
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
			if (timeEqualsOrCloseEnough(currentA.getStartTime(), currentB.getStartTime()) && nextB != null
					&& timeEqualsOrCloseEnough(currentA.getEndTime(), nextB.getEndTime())) {
				// Perfect Split
				resultList
						.add(new SubtitleMatch(currentA, new Subtitle[]{currentB, nextB}, MatchingMode.SPLIT_2B_IS_1A));
				indexA++;
				indexB += 2;
				continue;
			}
			if (timeEqualsOrCloseEnough(currentB.getStartTime(), currentA.getStartTime()) && nextA != null
					&& timeEqualsOrCloseEnough(currentB.getEndTime(), nextA.getEndTime())) {
				// Perfect Split
				resultList
						.add(new SubtitleMatch(new Subtitle[]{currentA, nextA}, currentB, MatchingMode.SPLIT_2A_IS_1B));
				indexA += 2;
				indexB++;
				continue;
			}
			if (timeBeforeOrCloseEnough(currentA.getStartTime(), currentB.getStartTime()) && nextB != null
					&& timeAfterOrCloseEnough(currentA.getEndTime(), nextB.getEndTime())) {
				// Approx Split
				resultList
						.add(new SubtitleMatch(currentA, new Subtitle[]{currentB, nextB}, MatchingMode.SPLIT_2B_IS_1A));
				indexA++;
				indexB += 2;
				continue;
			}
			if (timeBeforeOrCloseEnough(currentB.getStartTime(), currentA.getStartTime()) && nextA != null
					&& timeAfterOrCloseEnough(currentB.getEndTime(), nextA.getEndTime())) {
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
						if (prevB != null && timeAfterOrCloseEnough(prevB.getEndTime(), curA.getStartTime())) {
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
						if (prevA != null && timeAfterOrCloseEnough(prevA.getEndTime(), curB.getStartTime())) {
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

	private static void createReSyncFile(File sourceFile, File translatedFile, File syncedFile, File translatedSyncFile)
			throws IOException {
		SubtitleFile srcSubFile = parse(sourceFile);
		List<Subtitle> srcSubList = srcSubFile.getSubList();
		LOGGER.fine("Nb subs for bad version-bad language: " + srcSubList.size());
		SubtitleFile toReSyncSubFile = parse(translatedFile);
		List<Subtitle> toResyncSubList = toReSyncSubFile.getSubList();
		LOGGER.fine("Nb subs for bad version-good language: " + toResyncSubList.size());
		SubtitleFile resyncSrcSubFile = parse(syncedFile);
		List<Subtitle> resyncSrcSubList = resyncSrcSubFile.getSubList();
		LOGGER.fine("Nb subs for good version-bad language: " + resyncSrcSubList.size());

		List<SubtitleMatch> resyncMatchList = computeMatchSameLanguage(srcSubList, resyncSrcSubList);

		SubtitleMatch.showStats(resyncMatchList);

		List<SubtitleMatch> translatedMatchList = computeMatchSameVersion(srcSubList, toResyncSubList);
		SubtitleMatch.showStats(translatedMatchList);

		Subtitle[] resyncTranslation = reSync(toResyncSubList, resyncMatchList, translatedMatchList);

		writeOutputFile(translatedSyncFile, resyncTranslation);
	}

	public static void main(String[] args) {
		try {
			String[] inputFiles = new String[]{
					// "/Users/wandrillemoussel/Downloads/Game of Thrones - 05x09 - The Dance of
					// Dragons.720p.0sec.English.C.orig.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/Game of Thrones - 05x09 - The Dance of
					// Dragons.0sec.French.C.orig.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/Game of Thrones - 05x09 - The Dance of
					// Dragons.DON.English.C.orig.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/Game of Thrones - 05x09 - The Dance of Dragons.DON.fr.srt"};
					// "/Users/wandrillemoussel/Downloads/The Blacklist - 02x08 - The
					// Decembrist.LOL.English.C.updated.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/The Blacklist - 02x08 - The
					// Decembrist.LOL.French.C.updated.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/The Blacklist - 02x08 - The
					// Decembrist.WEB-DL-NTb.English.C.updated.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/The Blacklist - 02x08 - The Decembrist.WEB-DL-NTb.fr.srt"};
					// "/Users/wandrillemoussel/Downloads/The Blacklist - 02x10 - Luther Braxton_
					// Conclusion.DIMENSION.English.HI.C.updated.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/The Blacklist - 02x10 - Luther Braxton_
					// Conclusion.DIMENSION.French.C.updated.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/The Blacklist - 02x10 - Luther Braxton_
					// Conclusion.WEB-DL.English.HI.C.updated.Addic7ed.com.srt",
					// "/Users/wandrillemoussel/Downloads/The Blacklist - 02x10 - Luther Braxton_
					// Conclusion.WEB-DL.fr.srt"};
					"/Users/wandrillemoussel/Downloads/The Blacklist - 02x09 - Luther Braxton.DIMENSION.English.HI.C.updated.Addic7ed.com.srt",
					"/Users/wandrillemoussel/Downloads/The Blacklist - 02x09 - Luther Braxton.DIMENSION.French.C.updated.Addic7ed.com.srt",
					"/Users/wandrillemoussel/Downloads/The Blacklist - 02x09 - Luther Braxton.WEB-DL.English.HI.C.updated.Addic7ed.com.srt",
					"/Users/wandrillemoussel/Downloads/The Blacklist - 02x08 - Luther Braxton.WEB-DL.fr.srt"};
			final String fileNamePrefix = promtForString("Subtitle FileName Prefix");

			System.out.println("\nAutoReSync for folder: " + workFolderName);
			final Map<String, Map<String, Path>> inputs = new LinkedHashMap<>();
			try (Stream<Path> stream = Files.walk(Paths.get(workFolderName))) {
				List<Path> matchFiles = stream.filter(p -> {
					String fn = p.getFileName().toString();
					return Files.isRegularFile(p) && fn.startsWith(fileNamePrefix) && fn.endsWith(".srt");

				}).collect(Collectors.toList());
				matchFiles.forEach(p -> {
					String fileName = p.getFileName().toString();
					String[] fileNameParts = fileName.substring(fileNamePrefix.length()).split("\\.", -1);

					if (fileNameParts.length > 2) {
						String version = fileNameParts[0];
						String lang = fileNameParts[1];
						System.out.println(version + "/" + lang + ": " + p.toString());
						if (!inputs.containsKey(version)) {
							inputs.put(version, new LinkedHashMap<>());
						}
						inputs.get(version).put(lang, p);
					}
				});
			}
			String movieVersion = promtForString("Version of your movie " + inputs.keySet());
			Optional<String> originalVersion = inputs.keySet().stream().filter(k -> {
				return !k.equals(movieVersion);
			}).findFirst();

			Optional<String> syncedLang;
			Optional<String> translationLang;
			if (inputs.get(movieVersion).size() == 1) {
				syncedLang = Optional.of(inputs.get(movieVersion).keySet().iterator().next());
				translationLang = inputs.get(originalVersion.get()).keySet().stream().filter(k -> {
					return !k.equalsIgnoreCase(syncedLang.get());
				}).findFirst();
			} else {
				List<String> langs = Arrays.asList(inputs.get(originalVersion.get()).keySet().toArray(new String[0]));
				translationLang = Optional.of(promtForString("Target Language " + langs.toString()));
				syncedLang = inputs.get(originalVersion.get()).keySet().stream().filter(k -> {
					return !k.equals(translationLang);
				}).findFirst();
			}

			if (originalVersion.isPresent() && translationLang.isPresent()
					&& inputs.get(originalVersion.get()).containsKey(translationLang.get())) {
				File destination = new File(inputs.get(movieVersion).get(syncedLang.get()).toString()
						.replaceFirst(syncedLang.get(), translationLang.get()));
				createReSyncFile(inputs.get(originalVersion.get()).get(syncedLang.get()).toFile(),
						inputs.get(originalVersion.get()).get(translationLang.get()).toFile(),
						inputs.get(movieVersion).get(syncedLang.get()).toFile(), destination);

			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	static SubtitleFile parse(File file) throws IOException {
		SubtitleFile subFile = new SubtitleFile();
		String content = new String(Files.readAllBytes(file.toPath()));

		Pattern p = Pattern.compile(
				"(\\d+)\\r\\n(\\d{1,2}:\\d{1,2}:\\d{1,2},\\d{1,3}) --> (\\d{1,2}:\\d{1,2}:\\d{1,2},\\d{1,3})\\r\\n"
						+ "([\\s\\S]*?)(?=\\r\\n\\r\\n|$)");
		Matcher ma = p.matcher(content);
		int matchNum = 0;
		while (ma.find()) {
			String num = ma.group(1);
			String start = ma.group(2);
			String end = ma.group(3);
			String text = ma.group(4);
			subFile.subList.add(new Subtitle(++matchNum, start, end, text));
		}
		return subFile;

	}

	public static String promtForString(String invite) {

		System.out.print("\n" + invite + ": ");
		try {
			String choice = inputScanner.nextLine();
			return choice;
		} catch (Exception e) {
		} finally {
		}
		return null;
	}
	private static Subtitle[] reSync(List<Subtitle> toResyncSubList, List<SubtitleMatch> resyncMatchList,
			List<SubtitleMatch> translatedMatchList) throws IOException {
		Subtitle[] resyncTranslation = new Subtitle[toResyncSubList.size()];

		AtomicListElement<SubtitleMatch> syncMatchList = new AtomicListElement<SubtitleMatch>(resyncMatchList);
		AtomicListElement<SubtitleMatch> transMatchList = new AtomicListElement<SubtitleMatch>(translatedMatchList);

		transMatchList.forEach(tMatch -> {
			if (tMatch.isMatch()) {
				final Integer srcNum = tMatch.subAList.get(0).getSubNumber();
				if (syncMatchList.moveUntil(SubtitleMatch.sameSubANumPredicate(srcNum))) {
					SubtitleMatch syncMatch = syncMatchList.get();
					if (syncMatch.isMatch() || MatchingMode.SPLIT_2B_IS_1A.equals(syncMatch.mode)) {
						Integer translatedNum = tMatch.subBList.get(0).getSubNumber();
						resyncTranslation[translatedNum - 1] = tMatch.subBList.get(0).resyncTo(syncMatch.subBList);
					} else
						if (MatchingMode.SPLIT_2A_IS_1B.equals(syncMatch.mode) && transMatchList.peekNext().isMatch()) {
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

		return resyncTranslation;
	}
	private static String[] splitAfterFirstIndexOf(String origin, String separator) {
		int splitIndex = origin.indexOf(separator) + separator.length();
		String before = origin.substring(0, splitIndex);
		String leftOver = origin.substring(splitIndex, origin.length());
		return new String[]{before, leftOver};
	}
	private static String[] splitAfterLastIndexOf(String origin, String separator) {
		int splitIndex = origin.lastIndexOf(separator) + separator.length();
		String before = origin.substring(0, splitIndex);
		String leftOver = origin.substring(splitIndex, origin.length());
		return new String[]{before, leftOver};
	}

	private static boolean timeAfterOrCloseEnough(LocalTime a, LocalTime b) {
		return timeAfterOrCloseEnough(a, b, 400, ChronoUnit.MILLIS);
	}

	private static boolean timeAfterOrCloseEnough(LocalTime a, LocalTime b, long precision,
			TemporalUnit precisionUnit) {
		return a.isAfter(b.minus(precision, precisionUnit));
	}

	private static boolean timeBeforeOrCloseEnough(LocalTime a, LocalTime b) {
		return timeBeforeOrCloseEnough(a, b, 400, ChronoUnit.MILLIS);
	}

	private static boolean timeBeforeOrCloseEnough(LocalTime a, LocalTime b, long precision,
			TemporalUnit precisionUnit) {
		return a.isBefore(b.plus(precision, precisionUnit));
	}

	private static boolean timeEqualsOrCloseEnough(LocalTime a, LocalTime b) {
		return timeEqualsOrCloseEnough(a, b, 400, ChronoUnit.MILLIS);
	}

	private static boolean timeEqualsOrCloseEnough(LocalTime a, LocalTime b, long precision,
			TemporalUnit precisionUnit) {
		return a.isAfter(b.minus(precision, precisionUnit)) && a.isBefore(b.plus(precision, precisionUnit));
	}

	private static boolean timeWithinSubTimes(LocalTime a, Subtitle sub) {
		return a.isAfter(sub.getStartTime()) && a.isBefore(sub.getEndTime());
	}

	private static void writeOutputFile(File translatedSyncFile, Subtitle[] resyncTranslation) throws IOException {
		final AtomicInteger print = new AtomicInteger(0);
		Arrays.asList(resyncTranslation).forEach(sub -> {
			if (sub != null) {
				LOGGER.fine(Subtitle.lineString(sub));
				print.incrementAndGet();
			}
		});

		LOGGER.info("Writing file : " + translatedSyncFile.getName());
		FileWriter outFileWriter = new FileWriter(translatedSyncFile);
		AtomicInteger subNum = new AtomicInteger(0);
		Arrays.asList(resyncTranslation).forEach(sub -> {
			if (sub != null) {
				try {
					int currentSubNum = subNum.incrementAndGet();
					// System.out.print(currentSubNum + SRT_SUB_SEP + sub.toString());
					outFileWriter.write(currentSubNum + SRT_SUB_SEP + sub.toString());
				} catch (Exception e) {
					if (LOGGER.isLoggable(Level.SEVERE)) {
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						PrintStream ps = new PrintStream(new BufferedOutputStream(out));
						e.printStackTrace(ps);
						LOGGER.severe("Error while writing output file: " + out.toString());
					}
				}
			}
		});
		outFileWriter.close();
	}
	private String lang;

	private List<Subtitle> subList = new ArrayList<>();

	public SubtitleFile() {
	}

	public String getLang() {
		return lang;
	}

	public List<Subtitle> getSubList() {
		return subList;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public void setSubList(List<Subtitle> subList) {
		this.subList = subList;
	}

	@Override
	public String toString() {
		StringBuffer str = new StringBuffer();

		AtomicInteger i = new AtomicInteger(0);
		subList.stream().map(s -> i.addAndGet(1) + "\r\n" + s.toString()).forEach(s -> str.append(s));
		return str.toString();
	}
}
