package org.moussel.resynchrosub;

import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

public class SubtitleSync {
	public static SubtitleSync computeSync(Subtitle sub1A, Subtitle sub1B,
			Subtitle sub2A, Subtitle sub2B) {
		double chrono1A = convertTimeToDouble(sub1A.getStartTime());
		double chrono1B = convertTimeToDouble(sub1B.getStartTime());
		double chrono2A = convertTimeToDouble(sub2A.getStartTime());
		double chrono2B = convertTimeToDouble(sub2B.getStartTime());

		double fact = (chrono2B - chrono1B) / (chrono2A - chrono1A);
		double off = chrono1B - (chrono1A * fact);

		return new SubtitleSync(fact, off);
	}

	public static LocalTime convertDoubleToTime(double value) {
		return LocalTime.ofNanoOfDay(new Long(Math.round(value * 1000000000)));
	}

	public static double convertTimeToDouble(LocalTime time) {
		return time.getLong(ChronoField.MILLI_OF_DAY) / 1000.0;
	}

	double factor = 1.0;

	double offset = 0.0;

	public SubtitleSync() {
		// TODO Auto-generated constructor stub
	}

	public SubtitleSync(double factor, double offset) {
		this.factor = factor;
		this.offset = offset;
	}

	public Subtitle applySync(Subtitle sub) {
		return applySync(sub, SyncMode.FULL);
	}

	public Subtitle applySync(Subtitle sub, SyncMode mode) {
		Subtitle syncedSub = new Subtitle();
		syncedSub.setText(sub.getText());
		syncedSub.setStartTime(applySyncOnTime(sub.getStartTime()));
		if (SyncMode.FULL.equals(mode)) {
			syncedSub.setEndTime(applySyncOnTime(sub.getEndTime()));
		} else if (SyncMode.START_ONLY.equals(mode)) {
			long subDurationMili = ChronoUnit.MILLIS.between(
					sub.getStartTime(), sub.getEndTime());
			syncedSub.setEndTime(syncedSub.getStartTime().plus(subDurationMili,
					ChronoUnit.MILLIS));
		}
		return syncedSub;
	}

	public double applySyncOnDouble(double source) {
		return source * factor + offset;
	}

	public LocalTime applySyncOnTime(LocalTime time) {
		return convertDoubleToTime(applySyncOnDouble(convertTimeToDouble(time)));
	}

}
