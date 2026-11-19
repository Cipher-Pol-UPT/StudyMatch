package com.studymatch.events;

import com.studymatch.models.AcademicYear;

// published when Staff starts a new year, other services react to it without the year knowing about them
public record AcademicYearStartedEvent(AcademicYear academicYear) {
}
