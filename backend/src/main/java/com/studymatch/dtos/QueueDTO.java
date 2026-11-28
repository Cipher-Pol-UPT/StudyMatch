package com.studymatch.dtos;

// a page of the queue, and how many in the whole queue are Open (#364)
public record QueueDTO(long openCount, PageDTO<QueueTicketDTO> tickets) {
}
