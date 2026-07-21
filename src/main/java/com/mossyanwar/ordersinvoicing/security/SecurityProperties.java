package com.mossyanwar.ordersinvoicing.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * API users. Passwords come from the environment; startup fails with a clear message if one is
 * missing rather than falling back to a default.
 */
@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @NotEmpty List<@Valid User> users,
        @Min(4) @Max(31) int bcryptStrength) {

    public record User(@NotBlank String username, @NotBlank String password, @NotEmpty List<Role> roles) {
    }
}
