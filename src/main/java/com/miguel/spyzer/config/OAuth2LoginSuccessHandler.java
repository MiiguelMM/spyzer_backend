package com.miguel.spyzer.config;

import com.miguel.spyzer.entities.User;
import com.miguel.spyzer.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${application.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String googleId = oAuth2User.getAttribute("sub");
        String avatar = oAuth2User.getAttribute("picture");

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .googleId(googleId)
                            .avatar(avatar)
                            .build();
                    return userRepository.save(newUser);
                });

        // Create a UserDetails object for token generation
        org.springframework.security.core.userdetails.User userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                "",
                Collections.singletonList(new SimpleGrantedAuthority("USER")));

        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("userId", user.getId());
        extraClaims.put("name", user.getName());
        extraClaims.put("email", user.getEmail());

        String jwtToken = jwtService.generateToken(extraClaims, userDetails);

        response.sendRedirect(frontendUrl + "/oauth2/redirect?token=" + jwtToken);
    }
}
