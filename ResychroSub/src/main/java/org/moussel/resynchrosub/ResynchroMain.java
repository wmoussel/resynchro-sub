package org.moussel.resynchrosub;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResynchroMain {
	static final Scanner inputScanner = new Scanner(System.in);
	static final Logger LOGGER = Logger.getLogger(ResynchroMain.class.getName());

	private static String workFolderName = "/Users/wandrillemoussel/Downloads/";

	public static void main(String[] args) {
		try {
			// final String fileNamePrefix = "Suits - 07x02 - The Statue-VLAD.";
			final String fileNamePrefix = promtForString("Subtitle FileName Prefix");

			// Get All SRT files with same fileNamePrefix and parse version and language
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
			// If one of the srt files is the only one with a version, then its version is same as your movie file
			Object[] versionsWithOneSubtitle = inputs.keySet().stream().filter(k -> {
				return inputs.get(k).size() == 1;
			}).toArray();
			final String movieVersion;
			if (versionsWithOneSubtitle != null && versionsWithOneSubtitle.length == 1) {
				movieVersion = versionsWithOneSubtitle[0].toString();
				System.out.println("Auto-detected Movie version: " + movieVersion);
			} else {
				movieVersion = promtForString("Version of your movie " + inputs.keySet());
			}
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
				ResynchroCore.createReSyncFile(inputs.get(originalVersion.get()).get(syncedLang.get()).toFile(),
						inputs.get(originalVersion.get()).get(translationLang.get()).toFile(),
						inputs.get(movieVersion).get(syncedLang.get()).toFile(), destination);

			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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

}
