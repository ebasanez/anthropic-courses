package com.bprojects.courses.claude.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.anthropic.AnthropicCitationDocument;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns uploaded files into the two shapes Claude accepts: vision {@link Media} for images,
 * and citation documents for everything else. Binary office formats are converted to text
 * first, since Anthropic only takes images, PDFs and plain text.
 */
final class DocumentHelper {

    private DocumentHelper() {
    }

    // Anthropic vision formats -> sent as Media on the user message.
    private static final Set<String> SUPPORTED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    // Text formats -> sent as plain-text citation documents.
    private static final Set<String> SUPPORTED_TEXT_TYPES =
            Set.of("text/csv", "text/plain", "text/markdown");

    // Filename fallback: browsers are inconsistent about text content types (a .csv can arrive
    // as text/csv, application/vnd.ms-excel, or application/octet-stream depending on the OS).
    private static final Set<String> SUPPORTED_TEXT_EXTENSIONS = Set.of(".csv", ".txt", ".md");

    // Word/PowerPoint: binary formats Anthropic cannot take directly, so their text is
    // extracted with Tika and sent as a plain-text citation document.
    private static final Set<String> SUPPORTED_OFFICE_TYPES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private static final Set<String> SUPPORTED_OFFICE_EXTENSIONS =
            Set.of(".doc", ".docx", ".ppt", ".pptx");

    private static final String ALLOWED_DESCRIPTION =
            "images (jpeg, png, gif, webp), pdf, text (csv, txt, md), and office documents (doc, docx, ppt, pptx)";

    // Formats worth handing to Claude's code-execution sandbox: data and documents it can load
    // programmatically. Excel appears only here -- it has no inline path, so resolveKind()
    // deliberately does not accept it.
    static final Set<String> ANALYSIS_EXTENSIONS =
            Set.of(".txt", ".csv", ".md", ".xls", ".xlsx", ".doc", ".docx", ".ppt", ".pptx");

    private static final Set<String> ANALYSIS_TYPES = Set.of(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** True, when the upload is something, the code-execution sandbox can load and analyze. */
    static boolean isAnalysable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String contentType = file.getContentType();
        if (contentType != null
                && (SUPPORTED_TEXT_TYPES.contains(contentType)
                || SUPPORTED_OFFICE_TYPES.contains(contentType)
                || ANALYSIS_TYPES.contains(contentType))) {
            return true;
        }
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        return ANALYSIS_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /**
     * The file a "file analysis" request should run against. The checkbox in the UI is not a
     * trust boundary, so this rejects requests that ask for analysis without a usable file.
     */
    static MultipartFile firstAnalysable(MultipartFile[] files) {
        if (files != null) {
            for (MultipartFile file : files) {
                if (isAnalysable(file)) {
                    return file;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File analysis requires an attached file of one of: " + ANALYSIS_EXTENSIONS);
    }

    /** How an uploaded file is delivered to Claude. */
    private enum AttachmentKind { IMAGE, PDF, TEXT, OFFICE }

    // Images go to the user message as Media (vision). PDFs and text files go to the request
    // options as citation documents (citations enabled) so answers can cite pages/passages.
    record Attachments(List<Media> media, List<AnthropicCitationDocument> citationDocuments) {}

    static Attachments toAttachments(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return new Attachments(null, null);
        }
        try {
            List<Media> media = new ArrayList<>();
            List<AnthropicCitationDocument> citationDocs = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                AttachmentKind kind = resolveKind(file);
                if (kind == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unsupported attachment '" + file.getOriginalFilename() + "' (type '"
                                    + file.getContentType() + "'). Allowed: " + ALLOWED_DESCRIPTION);
                }
                switch (kind) {
                    case PDF -> citationDocs.add(AnthropicCitationDocument.builder()
                            .pdf(file.getBytes())
                            .title(file.getOriginalFilename())
                            .citationsEnabled(true)
                            .build());
                    // Anthropic has no binary block for text files, but the Citations API takes
                    // them as plain-text documents -> Claude reads the content and can cite it.
                    case TEXT -> citationDocs.add(AnthropicCitationDocument.builder()
                            .plainText(new String(file.getBytes(), StandardCharsets.UTF_8))
                            .title(file.getOriginalFilename())
                            .citationsEnabled(true)
                            .build());
                    // Word/PowerPoint are binary: extract their text first, then send it the
                    // same way. Formatting, embedded images and charts are lost.
                    case OFFICE -> citationDocs.add(AnthropicCitationDocument.builder()
                            .plainText(extractText(file))
                            .title(file.getOriginalFilename())
                            .citationsEnabled(true)
                            .build());
                    case IMAGE -> media.add(Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                            .data(new ByteArrayResource(file.getBytes()))
                            .build());
                }
            }
            return new Attachments(media.isEmpty() ? null : media,
                    citationDocs.isEmpty() ? null : citationDocs);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded attachment", e);
        }
    }

    /** Classify an upload by content type, falling back to its extension. Null when unsupported. */
    @Nullable
    private static AttachmentKind resolveKind(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            if (SUPPORTED_IMAGE_TYPES.contains(contentType)) {
                return AttachmentKind.IMAGE;
            }
            if (MediaType.APPLICATION_PDF_VALUE.equals(contentType)) {
                return AttachmentKind.PDF;
            }
            if (SUPPORTED_TEXT_TYPES.contains(contentType)) {
                return AttachmentKind.TEXT;
            }
            if (SUPPORTED_OFFICE_TYPES.contains(contentType)) {
                return AttachmentKind.OFFICE;
            }
        }
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (name.endsWith(".pdf")) {
            return AttachmentKind.PDF;
        }
        if (SUPPORTED_TEXT_EXTENSIONS.stream().anyMatch(name::endsWith)) {
            return AttachmentKind.TEXT;
        }
        if (SUPPORTED_OFFICE_EXTENSIONS.stream().anyMatch(name::endsWith)) {
            return AttachmentKind.OFFICE;
        }
        return null;
    }

    /**
     * Extract plain text from a Word/PowerPoint upload with Tika (same reader the RAG
     * ingestion uses). The filename is exposed so Tika can detect the format.
     */
    private static String extractText(MultipartFile file) throws IOException {
        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();   // extension = Tika format hint
            }
        };
        String text = new TikaDocumentReader(resource).read().stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No readable text found in '" + file.getOriginalFilename() + "'.");
        }
        return text;
    }
}
