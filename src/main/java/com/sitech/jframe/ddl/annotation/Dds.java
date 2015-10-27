package com.sitech.jframe.ddl.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sitech.jframe.ddl.datasource.ReadWriteEnum;

@Inherited
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Dds {
	String dataSourceKey() default "DEFAULT";
	ReadWriteEnum readWriteSeparate() default ReadWriteEnum.WRITE;
}
