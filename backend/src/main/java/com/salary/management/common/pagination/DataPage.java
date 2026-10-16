package com.salary.management.common.pagination;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable page of results with derived pagination metadata.
 *
 * <p>Only {@code content}, {@code page}, {@code size}, and {@code totalElements} are canonical
 * inputs. The remaining four components ({@code totalPages}, {@code first}, {@code last},
 * {@code empty}) are recomputed by the compact constructor and any values passed for them are
 * ignored.</p>
 *
 * @param <T> element type of the page content
 */
public record DataPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements) {

    public DataPage {
        content = content != null ? new ArrayList<>(content) : new ArrayList<>();
        size = size > 0 ? size : 20;
    }

    public int totalPages() {
        return (int) Math.ceil((double) totalElements / size);
    }

    public boolean first() { return page == 0; }
    public boolean last()  { return page >= totalPages() - 1 || totalPages() == 0; }
    public boolean isEmpty() { return content.isEmpty(); }

    public static <T> DataPage<T> empty() {
        return new DataPage<>(List.of(), 0, 20, 0L);
    }

    public <R> DataPage<R> map(Function<T, R> mapper) {
        return new DataPage<>(
                content.stream().map(mapper).collect(Collectors.toList()),
                page, size, totalElements);
    }

    public boolean hasContent() { return !content.isEmpty(); }
    public int numberOfElements() { return content.size(); }
}