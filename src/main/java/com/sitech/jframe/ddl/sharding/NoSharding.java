package com.sitech.jframe.ddl.sharding;

import com.sitech.jframe.ddl.datasource.ReadWriteEnum;

public class NoSharding extends AbstractSharding {

	@Override
	public String getDataSourceKey(String statementId, String sql, Object param) {
		// TODO Auto-generated method stub
		return null;
	}

	
	@Override
	public ReadWriteEnum getReadWrite(String statementId, String sql, Object param) {
		return null;
	}
	
}
