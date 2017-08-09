package org.moussel.resynchrosub;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class ResynchroTimeHelper {

	static boolean timeAfterOrCloseEnough(LocalTime a, LocalTime b) {
		return timeAfterOrCloseEnough(a, b, 400, ChronoUnit.MILLIS);
	}

	static boolean timeAfterOrCloseEnough(LocalTime a, LocalTime b, long precision, TemporalUnit precisionUnit) {
		return a.isAfter(b.minus(precision, precisionUnit));
	}

	static boolean timeBeforeOrCloseEnough(LocalTime a, LocalTime b) {
		return timeBeforeOrCloseEnough(a, b, 400, ChronoUnit.MILLIS);
	}

	static boolean timeBeforeOrCloseEnough(LocalTime a, LocalTime b, long precision, TemporalUnit precisionUnit) {
		return a.isBefore(b.plus(precision, precisionUnit));
	}

	static boolean timeEqualsOrCloseEnough(LocalTime a, LocalTime b) {
		return timeEqualsOrCloseEnough(a, b, 400, ChronoUnit.MILLIS);
	}

	static boolean timeEqualsOrCloseEnough(LocalTime a, LocalTime b, long precision, TemporalUnit precisionUnit) {
		return a.isAfter(b.minus(precision, precisionUnit)) && a.isBefore(b.plus(precision, precisionUnit));
	}

	static boolean timeWithinSubTimes(LocalTime a, Subtitle sub) {
		return a.isAfter(sub.getStartTime()) && a.isBefore(sub.getEndTime());
	}
}
