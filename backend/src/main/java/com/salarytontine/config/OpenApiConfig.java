package com.salarytontine.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    private static final String COOKIE_SCHEME_NAME = "cookieAuth";

    private static final String DESCRIPTION = """
            API de gestion de tontines salariales simulees.

            ### Authentification
            L'authentification s'effectue via `POST /api/auth/login`, qui depose un cookie
            `HttpOnly` contenant un JWT. Les appels suivants transmettent automatiquement
            ce cookie ; aucun jeton n'est manipule par le JavaScript du navigateur.

            ### Roles
            - `EMPLOYEE` : consulte son profil et ses salaires simules, parcourt les tontines
              ouvertes et demande a en rejoindre une.
            - `ACCOUNTANT` : le comptable. Créé et anime les tontines, arbitre les demandes
              d'adhesion, voit le salaire de base de chaque employé et declenche les
              prélèvements mensuels.
            - `ADMIN` : crée les comptes, attribue les roles, et dispose des mêmes droits
              que le comptable sur les tontines et les salaires.
            """;

    private final String cookieName;
    private final String serverPort;

    public OpenApiConfig(AppProperties appProperties,
                         @Value("${server.port:8080}") String serverPort) {
        this.cookieName = appProperties.getJwt().getCookieName();
        this.serverPort = serverPort;
    }

    @Bean
    public OpenAPI salaryTontineOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SalaryTontine API")
                        .version("1.0.0")
                        .description(DESCRIPTION)
                        .contact(new Contact().name("Projet académique SalaryTontine"))
                        .license(new License().name("Usage académique")))
                .servers(List.of(new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Environnement de developpement local")))
                .components(new Components().addSecuritySchemes(COOKIE_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(cookieName)
                                .description("Cookie HttpOnly depose par POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_SCHEME_NAME));
    }
}
