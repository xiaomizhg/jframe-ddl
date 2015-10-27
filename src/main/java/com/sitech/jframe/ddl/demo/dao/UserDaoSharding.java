package com.sitech.jframe.ddl.demo.dao;

import com.sitech.jframe.ddl.demo.dto.User;
import com.sitech.jframe.ddl.sharding.AbstractSharding;

public class UserDaoSharding extends AbstractSharding {


	@Override
	public String getDataSourceKey(String statementId, String sql, Object param) {
		User user = (User) param;
		if (user.getId() < 100) {
			return "partition1";
		} else {
			return "partition2";
		}
	}

}
