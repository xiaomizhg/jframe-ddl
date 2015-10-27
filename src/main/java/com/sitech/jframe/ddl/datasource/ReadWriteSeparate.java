package com.sitech.jframe.ddl.datasource;


/**
 * ReadWriteEnum  确定是读或者是写
 * @author zhangsf
 *
 */
public class ReadWriteSeparate {
	
	private ReadWriteEnum readWriteEnum;
	
	private ReadWriteSeparate oldReadWriteSeparate;
	
	
	public ReadWriteSeparate() {
//		this.readWriteEnum = ReadWriteEnum.WRITE;
	}
	
	
	public ReadWriteSeparate(ReadWriteEnum readWriteEnum) {
		this.readWriteEnum = readWriteEnum;
	}


	public ReadWriteEnum getReadWriteEnum() {
		return readWriteEnum;
	}
	
	public void newReadWriteEnum(ReadWriteEnum readWriteEnum) {
		this.readWriteEnum = readWriteEnum;
	}
	
	public void bindToThread() {
		this.oldReadWriteSeparate = DynamicDataSourceContextHolder.getReadWriteSeparate();
		DynamicDataSourceContextHolder.setReadWriteSeparate(this);
	}
	
	public void restoreThreadLocalStatus() {
		DynamicDataSourceContextHolder.setReadWriteSeparate(this.oldReadWriteSeparate);
	}
	

}
