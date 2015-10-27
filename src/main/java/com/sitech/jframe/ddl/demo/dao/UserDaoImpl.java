package com.sitech.jframe.ddl.demo.dao;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.mybatis.spring.support.SqlSessionDaoSupport;

import com.sitech.jframe.ddl.demo.dto.User;
import com.sitech.jframe.ddl.demo.sqlmap.UserMapper;
import com.sitech.jframe.ddl.spring.support.mybatis.DynamicSqlSessionDaoSupport;

public class UserDaoImpl extends DynamicSqlSessionDaoSupport implements UserDao {
	
	@Resource
	private UserMapper userMapper;
	
	@Resource
	private UserDao2 user2Svc;

	@Override
	public List<User> queryUserListMapper(User user) {
//		IUser retuser = super.getSqlSession().selectOne("com.sitech.jframe.ddl.demo.sqlmap.IUserMapper.selectUser", user);
//		return retuser;
		return userMapper.selectUser(user);
	}
	
	@SuppressWarnings({ "unused", "rawtypes" })
	@Override
	public List<User> queryUserList(User user) {
		
		List<User> list = super.getSqlSession().selectList("com.sitech.jframe.ddl.demo.sqlmap.UserMapper.selectUser", user);
		
		return list;
	}

	@Override
	public void insertUser(User user) {
//		if (user.getUserId() == 2) {
//			throw new RuntimeException("Rollback");
//		}
		
//		getSqlSession().insert("com.sitech.jframe.ddl.demo.sqlmap.UserMapper.insertUser", user);
		
		User user2 = new User();
		user2.setId(99);
		user2.setName("9999");
		getSqlSession().insert("com.sitech.jframe.ddl.demo.sqlmap.UserMapper.insertUser", user2);
		
		
//		User user3 = new User();
//		user3.setId(3);
//		user3.setName("cccccccccc");
//		user2Svc.newInsertUser(user3);
	}

	@Override
	public void deleteUser() {
		getSqlSession().delete("com.sitech.jframe.ddl.demo.sqlmap.UserMapper.deleteUser");
	}


}
