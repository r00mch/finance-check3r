package com.financetracker.auth;

import com.financetracker.common.ConflictException;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    // Compared against when the email is unknown, so login takes the same time either way
    private final String dummyHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password())));
        return jwt.issueFor(user.getId());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalise(request.email())).orElse(null);
        boolean matches = passwordEncoder.matches(
                request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            // same message for "no such user" and "wrong password" so emails cannot be enumerated
            throw new BadCredentialsException("Invalid email or password");
        }
        return jwt.issueFor(user.getId());
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
