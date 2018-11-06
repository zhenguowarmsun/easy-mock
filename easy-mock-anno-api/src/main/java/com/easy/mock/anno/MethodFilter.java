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
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface MethodFilter {

    /**
     * 须要方法（为空表示全部）
     * @return
     */
    String[] selected() default {};

    /**
     * 排除方法(优先级最高)
     * @return
     */
    String[] excluded() default {};

    /**
     * 是否需要继承方法
     * @return
     */
    boolean enableExtend() default true;
}
