package com.jxc.erp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * 全局异常处理：统一错误响应 {"success":false,"error":"..."}，避免裸 500。
 * 业务错误由各 Controller 用 ApiResponse.fail 返回（HTTP 200），
 * 未捕获异常在此兜底（HTTP 500 + 日志），同时规范化常见框架异常。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 未捕获异常 → 500（记录日志，不泄露内部细节） */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("未捕获异常: {}", e.getMessage(), e);
        return ApiResponse.fail("服务器内部错误：" + safeMessage(e));
    }

    /** 参数校验失败 → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return ApiResponse.fail("参数错误：" + msg);
    }

    /** 静态资源不存在（API 路径写错时的友好提示） */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(NoResourceFoundException e) {
        return ApiResponse.fail("接口不存在：" + e.getResourcePath());
    }

    /** 方法不支持（路径存在但 HTTP 方法不对）→ 405（原来落入 Exception 兜底成 500） */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Map<String, Object> handleMethodNotAllowed(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ApiResponse.fail("请求方法不支持：" + e.getMethod() + "（该接口支持 " + String.join("/", e.getSupportedMethods()) + "）");
    }

    /** Content-Type 不支持 → 415（如对 multipart 接口发 JSON） */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Map<String, Object> handleMediaType(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        return ApiResponse.fail("Content-Type 不支持：" + e.getContentType() + "（该接口需要 " + e.getSupportedMediaTypes() + "）");
    }

    /** 请求体解析失败 → 400（空 body/JSON 格式错） */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return ApiResponse.fail("请求体格式错误：无法解析 JSON");
    }

    /** 路径参数/查询参数类型转换失败 → 400（如 {id} 传了非数字） */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        return ApiResponse.fail("参数类型错误：" + e.getName() + " 需要 " + (e.getRequiredType() == null ? "正确类型" : e.getRequiredType().getSimpleName()));
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 120 ? m.substring(0, 120) : m;
    }
}
