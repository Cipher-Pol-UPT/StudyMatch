package com.studymatch.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// only the name, the curricular years are fixed when the degree is created
public record RenameDegreeRequestDTO(@NotBlank @Size(max = 100) String name) {

}
