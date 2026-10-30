package dev.peluci.doceenccoreapi.shared.error;

public enum ErrorCode {

    INTERNAL_ERROR,
    UNAUTHENTICATED,
    ACCESS_DENIED,
    INVALID_CREDENTIALS,
    MISSING_CSRF_HEADER,
    TOO_MANY_LOGIN_ATTEMPTS

}
