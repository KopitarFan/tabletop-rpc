package dev.boardgamerpc.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Builds the human- and machine-readable OpenAPI contract exposed by Springdoc. */
@Configuration
class OpenApiConfiguration {
    @Bean
    fun boardGameOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
            .title("MeepleRPC")
            .version("0.1.0")
            .summary("Authoritative APIs for online tabletop games")
            .description(
                "Create game sessions and submit versioned, idempotent commands for boards, " +
                    "cards, dice, pieces, turns, and custom tabletop state.",
            )
            .contact(Contact().name("MeepleRPC contributors"))
                .license(License().name("MIT").identifier("MIT")),
        )
        .tags(
            listOf(
                Tag().name("system").description("Service health and implementation metadata."),
                Tag().name("templates").description("Discover registered game rules and player limits."),
                Tag().name("games").description("Create and inspect authoritative game sessions."),
                Tag().name("players").description("Join players to available lobby seats."),
                Tag().name("commands").description(
                    "Atomically validate and apply versioned, idempotent player intent.",
                ),
                Tag().name("events").description("Synchronize ordered changes after a known sequence."),
            ),
        )
}
