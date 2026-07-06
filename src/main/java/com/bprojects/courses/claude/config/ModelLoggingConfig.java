package com.bprojects.courses.claude.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Logs every HTTP round-trip to the Anthropic model at DEBUG, including each
 * intermediate tool-call request/response (every tool round is a separate call).
 * Spring AI's Anthropic client is OkHttp-based, so we hook it via the OkHttp builder.
 */
@Configuration
public class ModelLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger("model.wire");
    // Cap response-body logging so we never buffer a huge/streaming payload.
    private static final long MAX_BODY_BYTES = 1_000_000L;

    @Bean
    AnthropicHttpClientBuilderCustomizer modelLoggingHttpClientCustomizer() {
        return builder -> builder.interceptor(new LoggingInterceptor());
    }

    static class LoggingInterceptor implements Interceptor {

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final ObjectWriter PRETTY = MAPPER.writerWithDefaultPrettyPrinter();

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            if (log.isDebugEnabled()) {
                log.debug("--> {} {}\n{}", request.method(), request.url(), prettyJson(requestBody(request)));
            }

            Response response = chain.proceed(request);

            if (log.isDebugEnabled()) {
                log.debug("<-- {} {}\n{}", response.code(), request.url(), prettyJson(responseBody(response)));
            }
            return response;
        }

        // Re-indent JSON for readability; leave non-JSON payloads untouched.
        private String prettyJson(String body) {
            try {
                return PRETTY.writeValueAsString(MAPPER.readTree(body));
            } catch (Exception e) {
                return body;
            }
        }

        private String requestBody(Request request) throws IOException {
            RequestBody body = request.body();
            if (body == null) {
                return "(no body)";
            }
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readUtf8();
        }

        private String responseBody(Response response) throws IOException {
            MediaType contentType = response.body() != null ? response.body().contentType() : null;
            if (contentType != null && "event-stream".equals(contentType.subtype())) {
                return "(streaming body not logged)";
            }
            // peekBody copies the body without consuming it, so the caller still reads it.
            return response.peekBody(MAX_BODY_BYTES).string();
        }
    }
}
