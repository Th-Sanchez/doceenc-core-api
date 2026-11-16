package dev.peluci.doceenccoreapi.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record PageResponse<T>(
        @Schema(requiredMode = REQUIRED) List<T> items,
        @Schema(requiredMode = REQUIRED) int page,
        @Schema(requiredMode = REQUIRED) int size,
        @Schema(requiredMode = REQUIRED) long totalElements,
        @Schema(requiredMode = REQUIRED) int totalPages) {

    public static <S, T> PageResponse<T> from (Page<S> page, Function<? super S, ? extends T> mapper) {
        List<T> items = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

}
