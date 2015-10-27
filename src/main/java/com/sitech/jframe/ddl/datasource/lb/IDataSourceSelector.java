package com.sitech.jframe.ddl.datasource.lb;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * 数据源选择接口
 * 可以从一个 list 中根据策略选择数据源
 * @author zhangsf
 *
 */
public interface IDataSourceSelector {
	
	public DataSource selector(String lookupKey);
	
	public void setDataSources(Map<String, List<DataSource>> dataSources);
	
}
