package com.sitech.jframe.ddl.datasource;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

/**
 * 数据源线程上下文变量
 * lookupKeyTL  DataSourceLookupKey 线程变量
 * readWriteSeparateTL  ReadWriteSeparate 线程变量
 * @author zhangsf
 *
 */
public class DynamicDataSourceContextHolder {
	
	private static ThreadLocal<DataSourceLookupKey> lookupKeyTL = new ThreadLocal<DataSourceLookupKey>();
	
	private static ThreadLocal<ReadWriteSeparate> readWriteSeparateTL = new ThreadLocal<ReadWriteSeparate>();
	
	
	public static void setDataSourceLookupKey(DataSourceLookupKey dataSourceLookupKey) {
		lookupKeyTL.set(dataSourceLookupKey);
	}
	
	public static DataSourceLookupKey getDataSourceLookupKey() {
		return lookupKeyTL.get();
	}
	
	public static void removeDataSourceLookupKey() {
		lookupKeyTL.remove();
	}
	
	
	public static void setReadWriteSeparate(ReadWriteSeparate readWriteSeparate) {
		readWriteSeparateTL.set(readWriteSeparate);
	}
	
	public static ReadWriteSeparate getReadWriteSeparate() {
		return readWriteSeparateTL.get();
	}
	
	public static void removeReadWriteSeparate() {
		readWriteSeparateTL.remove();
	}
	
	
}
