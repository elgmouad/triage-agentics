package com.triageai.tickets.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** Pagination envelope defined in README section 7.1. */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
