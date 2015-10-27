package com.sitech.jframe.ddl.datasource;

/**
 * DataSource  LookupKey
 * LookupKey 确定路由到具体的哪个数据库
 * @author zhangsf
 *
 */
public class DataSourceLookupKey {
	
	public static final String defaultDataSourceKey = "#DEFAULT#";
	
	private String dataSourceKey;
	
	
	private DataSourceLookupKey oldDataSourceLookupKey;
	
	
	
	public DataSourceLookupKey(String dataSourceKey) {
		this.dataSourceKey = dataSourceKey;
	}

	
	public String getDataSourceKey() {
		return dataSourceKey;
	}

	
	public void bindToThread() {
		this.oldDataSourceLookupKey = DynamicDataSourceContextHolder.getDataSourceLookupKey();
		DynamicDataSourceContextHolder.setDataSourceLookupKey(this);
	}
	
	public void restoreThreadLocalStatus() {
		DynamicDataSourceContextHolder.setDataSourceLookupKey(this.oldDataSourceLookupKey);
	}

}
