package com.mossyanwar.ordersinvoicing.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class)
            .withPropertyValues(
                    "app.security.bcrypt-strength=4",
                    "app.security.users[0].username=admin",
                    "app.security.users[0].roles=ADMIN");

    @EnableConfigurationProperties(SecurityProperties.class)
    static class PropertiesOnly {
    }

    @Test
    void startsWhenEveryUserHasAPassword() {
        context.withPropertyValues("app.security.users[0].password=s3cret")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(SecurityProperties.class).users().getFirst().roles()).containsExactly(Role.ADMIN);
                });
    }

    @Test
    void refusesToStartWhenAPasswordIsMissing() {
        context.withPropertyValues("app.security.users[0].password=")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().hasStackTraceContaining("users[0].password"));
    }
}
