package com.studymatch.events;

import com.studymatch.models.Student;

// published when a tutor confirms a room's attendance or gets rated, so their achievements are checked
public record TutorActivityEvent(Student tutor) {
}
