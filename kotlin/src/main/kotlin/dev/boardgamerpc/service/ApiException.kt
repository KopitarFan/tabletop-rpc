package dev.boardgamerpc.service

import org.springframework.http.HttpStatus

/**
 * Expected API failure carrying an HTTP status and JSON-serializable detail.
 * Rule conflicts use this exception rather than leaking framework exceptions.
 */
class ApiException(
    val status: HttpStatus,
    val detail: Any,
) : RuntimeException(detail.toString())
