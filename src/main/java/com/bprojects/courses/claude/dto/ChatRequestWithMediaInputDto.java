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

    /**
     * When true, one attached data file is analysed by Claude's code-execution sandbox
     * (uploaded via the Files API) and the findings are fed into the chat request.
     */
    private boolean fileAnalysis;

    @Nullable
    public MultipartFile[] getMedia() {
        return media;
    }

    public void setMedia(@Nullable MultipartFile[] media) {
        this.media = media;
    }

    public boolean isFileAnalysis() {
        return fileAnalysis;
    }

    public void setFileAnalysis(boolean fileAnalysis) {
        this.fileAnalysis = fileAnalysis;
    }
}
