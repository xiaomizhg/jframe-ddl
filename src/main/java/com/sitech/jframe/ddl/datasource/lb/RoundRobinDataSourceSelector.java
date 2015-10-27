package com.sitech.jframe.ddl.datasource.lb;

import java.util.Map;

import javax.sql.DataSource;

public class RoundRobinDataSourceSelector extends AbstractDataSourceSelector {
	
	// 1. DataSource 对象/字符串
	// 2. 权重对象
	private Map<DataSource, Integer> roundRobinMap;
	
	public RoundRobinDataSourceSelector(Map<DataSource, Integer> roundRobinMap) {
		this.roundRobinMap = roundRobinMap;
	}

	@Override
	public DataSource doSelector(String lookupKey) {
		// TODO Auto-generated method stub
		return null;
	}

}
