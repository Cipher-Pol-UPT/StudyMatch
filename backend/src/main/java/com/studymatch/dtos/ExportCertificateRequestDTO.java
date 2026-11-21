package com.studymatch.dtos;

import java.util.Set;

import com.studymatch.enums.Achievement;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// one achievement gives a certificate, more give a record with all of them
public record ExportCertificateRequestDTO(@NotEmpty Set<@NotNull Achievement> achievements,
    @NotNull @Pattern(regexp = "en|pt") String language) {

}
