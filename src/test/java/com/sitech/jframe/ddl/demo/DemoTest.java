package com.sitech.jframe.ddl.demo;

import static org.junit.Assert.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;

import com.sitech.jframe.ddl.annotation.Dds;
import com.sitech.jframe.ddl.demo.dao.UserDao;
import com.sitech.jframe.ddl.demo.dto.User;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:applicationContext.xml")
// @TestExecutionListeners(TransactionalTestExecutionListener.class)
// @Transactional
//public class DemoTest extends AbstractTransactionalJUnit4SpringContextTests {
public class DemoTest extends AbstractJUnit4SpringContextTests {

	@Resource
	public UserDao userSvc;

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	@Rollback(false)
	public void testQueryList() {
		User user = new User();
		user.setId(1);
		user.setOrderColumn("id");
		List<User> list = userSvc.queryUserList(user);
		for (User userConsole : list) {
			System.out.println(userConsole.toString());
		}
		
		user.setId(100);
		List<User> list2 = userSvc.queryUserList(user);
		for (User userConsole : list2) {
			System.out.println(userConsole.toString());
		}
	}
	
	@Test
	@Rollback(false)
	public void testQueryMapper() {
		User user = new User();
		user.setId(1);
		user.setOrderColumn("id");
		List<User> list = userSvc.queryUserListMapper(user);
		for (User userConsole : list) {
			System.out.println(userConsole.toString());
		}
	}

	@Test
	@Rollback(false)
	public void testInsert() {
		// 事务1
		User iUser1 = new User();
		iUser1.setId(1);
		iUser1.setName("zhangsf");
		userSvc.insertUser(iUser1);

		// 事务2
//		User iUser2 = new User();
//		iUser2.setId(200);
//		iUser2.setName("zhangsf2");
//		userSvc.insertUser(iUser2);

	}

	@Test
	@Rollback(false)
	public void testDel() {
		userSvc.deleteUser();
	}

}
