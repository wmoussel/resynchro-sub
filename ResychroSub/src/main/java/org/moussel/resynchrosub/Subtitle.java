package org.moussel.resynchrosub;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Subtitle {
	private static final String NEW_LINE = "\r\n";
	private static final DateTimeFormatter TIME_PATTERN = DateTimeFormatter.ofPattern("HH:mm:ss,SSS");

	public static String lineString(Subtitle sub) {
		if (sub == null) {
			return "";
		}
		return (sub.subNumber != null ? sub.subNumber + ") " : "") + sub.startTime.format(TIME_PATTERN) + " --> "
				+ sub.endTime.format(TIME_PATTERN) + " ("
				+ (ChronoUnit.MILLIS.between(sub.getStartTime(), sub.getEndTime()) / 1000.0) + "s)\t"
				+ sub.text.replaceAll("[\\n\\r]+", " / ");
	}

	public static String lineText(Subtitle sub) {
		return sub.text.replaceAll("[\\r\\n]+", " ").replaceAll("<[^>]*>", "").replaceAll("[-_\\.=+,*&♪]", "")
				.replaceAll("[\\s\\t]+", " ").replaceAll("^ | $", "").toLowerCase();
	}

	public static String lineTime(Subtitle sub) {
		return sub.startTime.format(TIME_PATTERN) + " --> " + sub.endTime.format(TIME_PATTERN) + " ("
				+ (ChronoUnit.MILLIS.between(sub.getStartTime(), sub.getEndTime()) / 1000.0) + "s)";
	}

	private LocalTime endTime;
	private LocalTime startTime;
	private Integer subNumber;

	private String text;
	private String textToCompare;
	private String timeToCompare;

	public Subtitle() {
		// TODO Auto-generated constructor stub
	}

	public Subtitle(int subNumber, String start, String end, String text) {
		this(start, end, text);
		this.subNumber = subNumber;
	}

	public Subtitle(String start, String end, String text) {
		this.startTime = LocalTime.parse(start, TIME_PATTERN);
		this.endTime = LocalTime.parse(end, TIME_PATTERN);
		this.text = text;
		this.textToCompare = lineText(this);
		this.timeToCompare = lineTime(this);
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public Integer getSubNumber() {
		return subNumber;
	}

	public String getText() {
		return text;
	}

	public String getTextToCompare() {
		return textToCompare;
	}

	public String getTimeToCompare() {
		return timeToCompare;
	}

	public List<Subtitle> resyncSplitFrom(List<Subtitle> subList) {
		List<Subtitle> resyncedList = new ArrayList<Subtitle>();
		double toStart = SubtitleSync.convertTimeToDouble(startTime);
		double toDuration = SubtitleSync.convertTimeToDouble(endTime) - toStart;
		double fromStart = SubtitleSync.convertTimeToDouble(subList.get(0).getStartTime());
		double fromDuration = SubtitleSync.convertTimeToDouble(subList.get(subList.size() - 1).getEndTime()) - fromStart;
		for (Subtitle fromSub : subList) {
			Subtitle resynced = new Subtitle();

			resynced.subNumber = fromSub.subNumber;
			double ratioStart = (SubtitleSync.convertTimeToDouble(fromSub.getStartTime()) - fromStart) / fromDuration;
			double ratioEnd = (SubtitleSync.convertTimeToDouble(fromSub.getEndTime()) - fromStart) / fromDuration;
			resynced.startTime = SubtitleSync.convertDoubleToTime(toStart + toDuration * ratioStart);
			resynced.endTime = SubtitleSync.convertDoubleToTime(toStart + toDuration * ratioEnd);
			resynced.text = fromSub.text;
			resyncedList.add(resynced);
		}
		return resyncedList;
	}

	public Subtitle resyncTo(List<Subtitle> subtitleList) {
		if (subtitleList == null || subtitleList.isEmpty()) {
			return null;
		}
		Subtitle resynced = new Subtitle();

		resynced.subNumber = this.subNumber;
		resynced.endTime = subtitleList.get(subtitleList.size() - 1).endTime;
		resynced.startTime = subtitleList.get(0).startTime;
		resynced.text = this.text;

		return resynced;
	}

	public Subtitle resyncTo(Subtitle subtitle) {
		Subtitle resynced = new Subtitle();

		resynced.subNumber = this.subNumber;
		resynced.endTime = subtitle.endTime;
		resynced.startTime = subtitle.startTime;
		resynced.text = this.text;

		return resynced;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public void setSubNumber(Integer subNumber) {
		this.subNumber = subNumber;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return startTime.format(TIME_PATTERN) + " --> " + endTime.format(TIME_PATTERN) + NEW_LINE + text + NEW_LINE
				+ NEW_LINE;
	}

}
