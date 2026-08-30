package com.salarytontine.security;

import com.salarytontine.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Transport du JWT via un cookie HttpOnly.
 * Le jeton n'est jamais accessible au JavaScript du navigateur,
 * ce qui evite son exfiltration en cas de XSS.
 */
@Service
public class JwtCookieService {

    private static final String COOKIE_PATH = "/";
    private static final String SAME_SITE_POLICY = "Lax";

    private final String cookieName;
    private final boolean secure;

    public JwtCookieService(AppProperties properties) {
        this.cookieName = properties.getJwt().getCookieName();
        this.secure = properties.getJwt().isCookieSecure();
    }

    public ResponseCookie buildAuthenticationCookie(String token, long maxAgeSeconds) {
        return baseCookie(token).maxAge(maxAgeSeconds).build();
    }

    /** Cookie vide et immediatement expire, utilise a la deconnexion. */
    public ResponseCookie buildExpiredCookie() {
        return baseCookie("").maxAge(0).build();
    }

    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public String getCookieName() {
        return cookieName;
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .sameSite(SAME_SITE_POLICY);
    }
}
