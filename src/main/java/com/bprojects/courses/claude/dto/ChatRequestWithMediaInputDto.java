package com.bprojects.courses.claude.dto;

import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Chat request for the multipart {@code POST} variants: the common parameters plus optional
 * file attachments (images for vision, PDFs sent as citation documents). Bound from the
 * multipart form, with each uploaded file arriving under the {@code media} part.
 */
public class ChatRequestWithMediaInputDto extends ChatRequestInputDto {

    @Nullable
    private MultipartFile[] media;

    @Nullable
    public MultipartFile[] getMedia() {
        return media;
    }

    public void setMedia(@Nullable MultipartFile[] media) {
        this.media = media;
    }
}
