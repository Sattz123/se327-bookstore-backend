package se327.bookstore.dto;

import java.time.Instant;

public record AdminCategoryDetailDto(Long id, String name, String imageUrl, boolean visible,
                                      long productCount, long version, Instant createdAt) { }
