package com.discographyx.app.web.rest.errors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.discographyx.app.security.UserNotActivatedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AuthenticationFailureTest {

    @Test
    void inactiveAccountsReturnUnauthorizedInsteadOfInternalServerError() throws Exception {
        MockMvcBuilders.standaloneSetup(new InactiveAccountController())
            .setControllerAdvice(new ExceptionTranslator(new MockEnvironment()))
            .build()
            .perform(get("/test/inactive-account"))
            .andExpect(status().isUnauthorized());
    }

    @RestController
    static class InactiveAccountController {

        @GetMapping("/test/inactive-account")
        void authenticate() {
            throw new InternalAuthenticationServiceException("Account is inactive", new UserNotActivatedException("Account is inactive"));
        }
    }
}
