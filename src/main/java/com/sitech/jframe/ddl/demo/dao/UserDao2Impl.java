package com.sitech.jframe.ddl.demo.dao;

import java.util.List;

import com.sitech.jframe.ddl.demo.dto.User;
import com.sitech.jframe.ddl.spring.support.mybatis.DynamicSqlSessionDaoSupport;

public class UserDao2Impl extends DynamicSqlSessionDaoSupport implements UserDao2 {


	@Override
	public void newInsertUser(User user) {
		
		getSqlSession().insert("com.sitech.jframe.ddl.demo.sqlmap.UserMapper.insertUser", user);
	
	}

}
