package com.studymatch.dtos;

import java.util.List;

// what blocking would do, shown before the admin confirms. currentEditions is only filled for teachers
public record BlockImpactDTO(AccountSummaryDTO account, int upcomingStudyRooms, int pendingTutorApplications,
    List<SubjectEditionResponseDTO> currentEditions) {

}
