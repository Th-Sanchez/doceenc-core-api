package dev.peluci.doceenccoreapi.auth;

import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final LoginThrottle throttle;


    public LoginService(AuthenticationManager authenticationManager, LoginThrottle throttle) {
        this.authenticationManager = authenticationManager;
        this.throttle = throttle;
    }

    Authentication authenticate(String rawEmail, String password) {
        String email = User.normalizeEmail(rawEmail);

        throttle.assertNotLocked(email);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            throttle.recordSuccess(email);
            return authentication;
        } catch (BadCredentialsException ex) {
            throttle.recordFailure(email);
            throw invalidCredentials();
        } catch (InternalAuthenticationServiceException ex) {
            throw new IllegalStateException("Authentication could not be performed", ex);
        } catch (AuthenticationException ex) {
            throw invalidCredentials();
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
                "Invalid email or password.");
    }
}
