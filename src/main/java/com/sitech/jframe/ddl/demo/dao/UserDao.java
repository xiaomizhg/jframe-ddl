package com.sitech.jframe.ddl.demo.dao;

import java.util.List;

import com.sitech.jframe.ddl.demo.dto.User;

public interface UserDao {
	
	public List<User> queryUserListMapper(User user);
	
	public List<User> queryUserList(User user);
	
	public void insertUser(User user);
	
	public void deleteUser();

}
