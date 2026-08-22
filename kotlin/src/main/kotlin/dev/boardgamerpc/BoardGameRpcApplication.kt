package dev.boardgamerpc

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Spring Boot entry point and component-scan root for the service. */
@SpringBootApplication
class BoardGameRpcApplication

/** Starts the embedded HTTP server. */
fun main(args: Array<String>) {
    runApplication<BoardGameRpcApplication>(*args)
}
