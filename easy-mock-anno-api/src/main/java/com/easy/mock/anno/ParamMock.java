package com.easy.mock.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * EasyMock标签
 *
 * @author zhenguowarmsun@163.com
 *
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ParamMock {

    String name();

    String doc() default "";

    int order() default 999;

    boolean required() default false;
}
