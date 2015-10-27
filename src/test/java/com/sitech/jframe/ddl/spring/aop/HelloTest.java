package com.sitech.jframe.ddl.spring.aop;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;

public class HelloTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws Exception {
		
		ProxyFactory proxyFactory=new ProxyFactory(new Class[]{IHello.class});
		proxyFactory.setTarget(new Hello());
		
		proxyFactory.addAdvice(new MethodBeforeAdvice() {

			@Override
			public void before(Method method, Object[] args, Object target)
					throws Throwable {
				System.out.println(" --------- MethodBeforeAdvice --------- ");
			}
		});
		
		
/*		proxyFactory.addAdvice(new AfterReturningAdvice() {

			@Override
			public void afterReturning(Object returnValue, Method method,
					Object[] args, Object target) throws Throwable {
				System.out.println(" --------- AfterReturningAdvice --------- ");
			}
		});
		
		
		proxyFactory.addAdvice(new MethodInterceptor() {
			@SuppressWarnings("unused")
			@Override
			public Object invoke(MethodInvocation invocation) throws Throwable {
				
				Method m = invocation.getMethod();
				Object thisObj = invocation.getThis();
				System.out.println(" ---------  执行前 ---------- ");
				Object obj = invocation.proceed();
				System.out.println(" ---------  执行后 ---------- ");
				return obj;
			}
			
		});*/
		
		
		Object proxyObj = proxyFactory.getProxy();
		if (proxyObj instanceof Advised) {
			System.out.println(" iiiiiiiiiii ");
			TargetSource ts = ((Advised)proxyObj).getTargetSource();
			Object target = ts.getTarget();
			System.out.println(target.getClass());
			if (target != null && !ts.isStatic()) {
				ts.releaseTarget(target);
			}
		}
	}

}
