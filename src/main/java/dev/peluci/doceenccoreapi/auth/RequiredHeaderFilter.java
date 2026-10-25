package dev.peluci.doceenccoreapi.auth;

import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import dev.peluci.doceenccoreapi.shared.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class RequiredHeaderFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Requested-With";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean changesState = !SAFE_METHODS.contains(request.getMethod());
        String header = request.getHeader(HEADER_NAME);
        if (changesState && (header == null || header.isBlank())) {
            ProblemResponseWriter.write(response, HttpStatus.FORBIDDEN, ErrorCode.MISSING_CSRF_HEADER,
                    "Forbidden", "Missing required header" + HEADER_NAME + ".");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
