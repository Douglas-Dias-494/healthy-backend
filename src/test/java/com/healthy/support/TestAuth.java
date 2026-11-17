package com.healthy.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class TestAuth {

    public static final String PASSWORD = "123456";

    private TestAuth() {
    }

    public static String bearer(MockMvc mvc, String profile, String email) throws Exception {
        String json = """
                {"perfil":"%s","email":"%s","senha":"%s"}""".formatted(profile, email, PASSWORD);
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.<String>read(response, "$.token");
    }
}
