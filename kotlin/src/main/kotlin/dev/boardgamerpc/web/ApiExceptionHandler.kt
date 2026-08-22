package dev.boardgamerpc.web

import dev.boardgamerpc.model.ApiError
import dev.boardgamerpc.service.ApiException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Converts expected engine failures into the protocol's stable error envelope. */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    /** Preserves the engine-selected status and exposes its structured detail. */
    fun handle(exception: ApiException): ResponseEntity<ApiError> =
        ResponseEntity.status(exception.status).body(ApiError(exception.detail))
}
