package com.studymatch.dtos;

// the blocked account and what was cancelled with it, for the confirmation message
public record BlockedAccountDTO(AccountSummaryDTO account, int cancelledStudyRooms, int cancelledTutorApplications) {

}
