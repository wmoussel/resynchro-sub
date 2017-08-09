package org.moussel.resynchrosub;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubtitleFile {
	static final Logger LOGGER = Logger.getLogger(SubtitleFile.class.getName());
	private static final String SRT_SUB_SEP = "\r\n";

	private String lang;

	private File srtFile;

	private List<Subtitle> subList = new ArrayList<>();

	public SubtitleFile() {
	}

	public SubtitleFile(File srtFile) {
		this.srtFile = srtFile;
	}

	public String getLang() {
		return lang;
	}

	public List<Subtitle> getSubList() {
		return subList;
	}

	public void parse() throws IOException {
		String content = new String(Files.readAllBytes(this.srtFile.toPath()));

		Pattern p = Pattern.compile(
				"(\\d+)\\r\\n(\\d{1,2}:\\d{1,2}:\\d{1,2},\\d{1,3}) --> (\\d{1,2}:\\d{1,2}:\\d{1,2},\\d{1,3})\\r\\n"
						+ "([\\s\\S]*?)(?=\\r\\n\\r\\n|$)");
		Matcher ma = p.matcher(content);
		int matchNum = 0;
		subList.clear();
		while (ma.find()) {
			String num = ma.group(1);
			String start = ma.group(2);
			String end = ma.group(3);
			String text = ma.group(4);
			subList.add(new Subtitle(++matchNum, start, end, text));
		}

	}

	public List<Subtitle> parseAndGetSubList() throws IOException {
		if (subList == null) {
			this.parse();
		}
		return getSubList();
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

	public void writeFile() throws IOException {
		final AtomicInteger print = new AtomicInteger(0);
		this.subList.forEach(sub -> {
			if (sub != null) {
				LOGGER.fine(Subtitle.lineString(sub));
				print.incrementAndGet();
			}
		});

		LOGGER.info("Writing file : " + this.srtFile.getName());
		FileWriter outFileWriter = new FileWriter(this.srtFile);
		AtomicInteger subNum = new AtomicInteger(0);
		this.subList.forEach(sub -> {
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
}
