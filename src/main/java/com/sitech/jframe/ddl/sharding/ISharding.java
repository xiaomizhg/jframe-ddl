package com.sitech.jframe.ddl.sharding;

import com.sitech.jframe.ddl.datasource.ReadWriteEnum;

public interface ISharding {
	
	String getDataSourceKey();
	
	ReadWriteEnum getReadWrite();
	
	void reset();

}
