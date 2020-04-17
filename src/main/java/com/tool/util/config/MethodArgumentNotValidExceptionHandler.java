package com.tool.util.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * @Description
 * @Author MCC
 * @Date 2020/4/17 11:16
 * @Version 1.0
 * @Valid 自定义返回结果工具类
 **/
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class MethodArgumentNotValidExceptionHandler<T> {

    @ResponseStatus(BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public T methodArgumentNotValidException(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        List<org.springframework.validation.FieldError> fieldErrors = result.getFieldErrors();
        return processFieldErrors(fieldErrors);
    }

    private T processFieldErrors(List<org.springframework.validation.FieldError> fieldErrors) {
        StringBuffer error = new StringBuffer();
        for (org.springframework.validation.FieldError fieldError : fieldErrors) {
            error.append(fieldError.getField()).append(fieldError.getDefaultMessage()).append(";");
        }
        return null;
    }

}
