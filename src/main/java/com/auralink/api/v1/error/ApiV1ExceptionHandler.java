package com.auralink.api.v1.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Order(Integer.MIN_VALUE)
@RestControllerAdvice(basePackages = {"com.auralink.api.v1", "com.auralink.controller.v1"})
public class ApiV1ExceptionHandler {
   private static final Logger log = LoggerFactory.getLogger(ApiV1ExceptionHandler.class);
   static final String CORRELATION_HEADER = "X-Correlation-ID";
   private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

   @ExceptionHandler(ApiV1Exception.class)
   public ResponseEntity<ApiV1ErrorResponse> handleApiException(ApiV1Exception exception, HttpServletRequest request) {
      return this.response(exception.getStatus(), exception.getCode(), exception.getMessage(), request, Map.of(), exception.getViolations());
   }

   @ExceptionHandler(CannotCreateTransactionException.class)
   public ResponseEntity<ApiV1ErrorResponse> handleCatalogTransactionUnavailable(CannotCreateTransactionException exception, HttpServletRequest request) {
      return this.response(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CATALOG_READ_UNAVAILABLE, "PostgreSQL 数据库暂时不可用", request, Map.of(), List.of());
   }

   @ExceptionHandler(MethodArgumentNotValidException.class)
   public ResponseEntity<ApiV1ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
      Map<String, String> errors = new LinkedHashMap<>();
      exception.getBindingResult().getAllErrors().forEach(error -> {
         String key = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
         errors.putIfAbsent(key, error.getDefaultMessage() == null ? "参数无效" : error.getDefaultMessage());
      });
      return this.response(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "请求参数验证失败", request, errors, List.of());
   }

   @ExceptionHandler(MethodArgumentTypeMismatchException.class)
   public ResponseEntity<ApiV1ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
      String parameter = exception.getName() == null ? "request" : exception.getName();
      return this.response(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "请求参数格式无效", request, Map.of(parameter, "参数类型或格式无效"), List.of());
   }

   @ExceptionHandler(HttpMessageNotReadableException.class)
   public ResponseEntity<ApiV1ErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
      return this.response(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "请求 JSON 格式无效", request, Map.of(), List.of());
   }

   @ExceptionHandler(MaxUploadSizeExceededException.class)
   public ResponseEntity<ApiV1ErrorResponse> handleMaximumUploadSize(MaxUploadSizeExceededException exception, HttpServletRequest request) {
      return this.response(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.ASSET_TOO_LARGE, "资源大小超过允许上限", request, Map.of(), List.of());
   }

   @ExceptionHandler(MissingServletRequestPartException.class)
   public ResponseEntity<ApiV1ErrorResponse> handleMissingMultipartPart(MissingServletRequestPartException exception, HttpServletRequest request) {
      return this.response(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "缺少必需的上传文件", request, Map.of(), List.of());
   }

   @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
   public ResponseEntity<ApiV1ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
      return this.response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "请求媒体类型不受支持", request, Map.of(), List.of());
   }

   @ExceptionHandler(Exception.class)
   public ResponseEntity<ApiV1ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
      String correlationId = this.correlationId(request);
      log.error("未处理的 /api/v1 异常，correlationId={}, type={}", correlationId, exception.getClass().getSimpleName());
      return this.response(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, "服务器内部错误", request, Map.of(), List.of(), correlationId);
   }

   private ResponseEntity<ApiV1ErrorResponse> response(
      HttpStatus status,
      ApiErrorCode code,
      String message,
      HttpServletRequest request,
      Map<String, String> validationErrors,
      List<ApiViolationDetail> violations
   ) {
      return this.response(status, code, message, request, validationErrors, violations, this.correlationId(request));
   }

   private ResponseEntity<ApiV1ErrorResponse> response(
      HttpStatus status,
      ApiErrorCode code,
      String message,
      HttpServletRequest request,
      Map<String, String> validationErrors,
      List<ApiViolationDetail> violations,
      String correlationId
   ) {
      ApiV1ErrorResponse body = new ApiV1ErrorResponse(
         Instant.now(), status.value(), code.name(), message, request.getRequestURI(), correlationId, validationErrors, violations
      );
      return ((BodyBuilder)ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).header("X-Correlation-ID", new String[]{correlationId})).body(body);
   }

   private String correlationId(HttpServletRequest request) {
      String supplied = request.getHeader("X-Correlation-ID");
      return supplied != null && SAFE_CORRELATION_ID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
   }
}
