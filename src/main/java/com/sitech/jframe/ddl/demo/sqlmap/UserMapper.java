package com.sitech.jframe.ddl.demo.sqlmap;

import java.util.List;

import com.sitech.jframe.ddl.demo.dto.User;

public interface UserMapper {
	
	public List<User> selectUser(User user);

}
