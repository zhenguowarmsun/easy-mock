package com.easy.mock.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 必要参数要求
 *
 * @author zhenguowarmsun@163.com
 *
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ParamFilter {

    /**
     * 须要字段（为空表示全部）
     * @return
     */
    String[] selected() default {};

    /**
     * 必填字段（优先级最低）
     * @return
     */
    String[] required() default {};

    /**
     * 排除字段(优先级最高)
     * @return
     */
    String[] excluded() default {};

}
