package dev.peluci.doceenccoreapi.shared.error;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ProblemResponseWriter {

    private ProblemResponseWriter() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, ErrorCode code,
                             String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type": "about:blank", "title": "%s", "status": %d, "detail": "%s", "code": "%s"}"""
                .formatted(title, status.value(), detail, code.name()));
    }

}
