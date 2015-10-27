package com.sitech.jframe.ddl.utils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * java 反射工具类
 * 
 * @author zhangsf
 * 
 */
public class ReflectionUtil {
	private static final Logger logger = LoggerFactory
			.getLogger(ReflectionUtil.class);

	/**
	 * 获取对象的 属性 值
	 * 
	 * @param target
	 * @param fieldName
	 * @return
	 */
	public static Object getFieldValue(final Object target,
			final String fieldName) {

		Field field = findField(target.getClass(), fieldName);
		if (field == null) {
			throw new IllegalArgumentException("Could not find field ["
					+ fieldName + "] on target [" + target + "]");
		}
		makeAccessible(field);

		Object result = null;
		try {
			result = field.get(target);
		} catch (IllegalAccessException ex) {
			handleReflectionException(ex);
		}
		return result;
	}

	/**
	 * 设置对象属性值
	 * 
	 * @param target
	 * @param fieldName
	 * @param value
	 */
	public static void setFieldValue(final Object target,
			final String fieldName, final Object value) {
		Field field = findField(target.getClass(), fieldName);
		if (field == null) {
			throw new IllegalArgumentException("Could not find field ["
					+ fieldName + "] on target [" + target + "]");
		}
		makeAccessible(field);

		try {
			field.set(target, value);
		} catch (IllegalAccessException ex) {
			handleReflectionException(ex);
		}
	}

	/**
	 * 调用对象方法
	 * 
	 * @param target
	 * @param methodName
	 * @param args
	 * @return
	 */
	public static Object invokeMethod(Object target, String methodName,
			Object... args) {
		Class<?>[] paramTypes = getParameterTypes(args);
		Method method = findMethod(target.getClass(), methodName, paramTypes);
		if (method == null) {
			throw new IllegalArgumentException("Could not find method ["
					+ methodName + "] on target [" + target + "]");
		}

		makeAccessible(method);

		try {
			return method.invoke(target, args);
		} catch (Exception ex) {
			handleReflectionException(ex);
		}
		throw new IllegalStateException("Should never get here");
	}

	private static Class[] getParameterTypes(Object[] args) {
		if (args == null || args.length == 0) {
			return null;
		}
		Class[] clazzs = new Class[args.length];
		for (int i = 0; i < args.length; i++) {
			clazzs[i] = args[i].getClass();
		}
		return clazzs;
	}

	/**
	 * 查找对象的属性 Field
	 * 
	 * @param clazz
	 * @param name
	 * @return
	 */
	public static Field findField(Class<?> clazz, String name) {
		return findField(clazz, name, null);
	}

	/**
	 * 查找对象属性
	 * 
	 * @param clazz
	 * @param name
	 * @param type
	 * @return
	 */
	public static Field findField(Class<?> clazz, String name, Class<?> type) {
		Class<?> searchType = clazz;
		while (!Object.class.equals(searchType) && searchType != null) {
			Field[] fields = searchType.getDeclaredFields();
			for (Field field : fields) {
				if ((name.equals(field.getName()))
						&& (type == null || type.equals(field.getType()))) {
					return field;
				}
			}
			searchType = searchType.getSuperclass();
		}
		return null;
	}

	/**
	 * 查找对象方法
	 * 
	 * @param clazz
	 * @param name
	 * @return
	 */
	public static Method findMethod(Class<?> clazz, String name) {
		return findMethod(clazz, name, new Class[0]);
	}

	/**
	 * 查找对象方法
	 * 
	 * @param clazz
	 * @param name
	 * @param paramTypes
	 * @return
	 */
	public static Method findMethod(Class<?> clazz, String name,
			Class<?>... paramTypes) {
		Class<?> searchType = clazz;
		while (searchType != null) {
			Method[] methods = (searchType.isInterface() ? searchType
					.getMethods() : searchType.getDeclaredMethods());
			for (Method method : methods) {
				if (name.equals(method.getName())
						&& (paramTypes == null || Arrays.equals(methodParameterTypesWrapper(paramTypes),
								methodParameterTypesWrapper(method.getParameterTypes())))) {
					return method;
				}
			}
			searchType = searchType.getSuperclass();
		}
		return null;
	}
	

	/**
	 * 使得私有变量可访问 , 控制 JAAS 授权机制的 权限检查 ， 只要运行时堆栈 本类有控制 Field 的权限，
	 * 则对堆栈中的其他类调用跳过Permission检查
	 * 
	 * @param field
	 */
	public static void makeAccessible(final Field field) {
		AccessController.doPrivileged(new PrivilegedAction<Field>() {
			@Override
			public Field run() {
				if ((!Modifier.isPublic(field.getModifiers()) || !Modifier
						.isPublic(field.getDeclaringClass().getModifiers()))
						&& !field.isAccessible()) {
					field.setAccessible(true);
				}
				return null;
			}
		});
	}

	public static void makeAccessible(final Method method) {

		AccessController.doPrivileged(new PrivilegedAction<Field>() {
			@Override
			public Field run() {
				if ((!Modifier.isPublic(method.getModifiers()) || !Modifier
						.isPublic(method.getDeclaringClass().getModifiers()))
						&& !method.isAccessible()) {
					method.setAccessible(true);
				}
				return null;
			}
		});
	}

	public static boolean isPublicStaticFinal(Field field) {
		int modifiers = field.getModifiers();
		return (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier
				.isFinal(modifiers));
	}

	public static boolean isEqualsMethod(Method method) {
		if (method == null || !method.getName().equals("equals")) {
			return false;
		}
		Class<?>[] paramTypes = method.getParameterTypes();
		return (paramTypes.length == 1 && paramTypes[0] == Object.class);
	}

	public static boolean isHashCodeMethod(Method method) {
		return (method != null && method.getName().equals("hashCode") && method
				.getParameterTypes().length == 0);
	}

	public static boolean isToStringMethod(Method method) {
		return (method != null && method.getName().equals("toString") && method
				.getParameterTypes().length == 0);
	}

	public static boolean isObjectMethod(Method method) {
		try {
			Object.class.getDeclaredMethod(method.getName(),
					method.getParameterTypes());
			return true;
		} catch (SecurityException ex) {
			return false;
		} catch (NoSuchMethodException ex) {
			return false;
		}
	}

	/**
	 * 判断是否可以访问私有方法
	 * 
	 * @return
	 */
	private static boolean canAccessPrivateMethods() {
		try {
			SecurityManager securityManager = System.getSecurityManager();
			if (null != securityManager) {
				securityManager.checkPermission(new ReflectPermission(
						"suppressAccessChecks"));
			}
		} catch (SecurityException e) {
			return false;
		}
		return true;
	}

	public static void handleReflectionException(Exception ex) {
		if (ex instanceof NoSuchMethodException) {
			throw new IllegalStateException("Method not found: "
					+ ex.getMessage());
		}
		if (ex instanceof IllegalAccessException) {
			throw new IllegalStateException("Could not access method: "
					+ ex.getMessage());
		}
		if (ex instanceof InvocationTargetException) {
			handleInvocationTargetException((InvocationTargetException) ex);
		}
		if (ex instanceof RuntimeException) {
			throw (RuntimeException) ex;
		}
		throw new UndeclaredThrowableException(ex);
	}

	public static void handleInvocationTargetException(
			InvocationTargetException ex) {
		rethrowRuntimeException(ex.getTargetException());
	}

	public static void rethrowRuntimeException(Throwable ex) {
		if (ex instanceof RuntimeException) {
			throw (RuntimeException) ex;
		}
		if (ex instanceof Error) {
			throw (Error) ex;
		}
		throw new UndeclaredThrowableException(ex);
	}
	
	
	private static Class<?>[] methodParameterTypesWrapper(Class<?>[] parameterTypes) {
		Class<?>[] wrappers = new Class[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			wrappers[i] = transToWrapper(parameterTypes[i]);
		}
		return wrappers;
	}
	
	private static Class<?> transToWrapper(Class basicClazz) {
		if(basicClazz == Integer.TYPE) {
			return Integer.class;
		} else if (basicClazz == Long.TYPE) {
			return Long.class;
		} else if (basicClazz == Short.TYPE) {
			return Short.class;
		} else if (basicClazz == Boolean.TYPE) {
			return Boolean.class;
		} else if (basicClazz == Float.TYPE) {
			return Float.class;
		} else if (basicClazz == Double.TYPE) {
			return Double.class;
		} else if (basicClazz == Character.TYPE) {
			return Character.class;
		} else if (basicClazz == Byte.TYPE) {
			return Byte.class;
		}
		return basicClazz;
	}
	
	private static Class<?> transToBasic(Class wrapperClazz) {
		if(wrapperClazz == Integer.class) {
			return Integer.TYPE;
		} else if (wrapperClazz == Long.class) {
			return Long.TYPE;
		} else if (wrapperClazz == Short.class) {
			return Short.TYPE;
		} else if (wrapperClazz == Boolean.class) {
			return Boolean.TYPE;
		} else if (wrapperClazz == Float.class) {
			return Float.TYPE;
		} else if (wrapperClazz == Double.class) {
			return Double.TYPE;
		} else if (wrapperClazz == Character.class) {
			return Character.TYPE;
		} else if (wrapperClazz == Byte.class) {
			return Byte.TYPE;
		}
		return wrapperClazz;
	}
	
	
	private static Object stringToType(String value, Class type) throws ParseException {
        Object rValue = null;
        if (type.equals(Integer.TYPE) || type.equals(Integer.class)) {
            rValue = Integer.parseInt(value); // String 转为 int 型
        } else if (type.equals(Long.TYPE) || type.equals(Long.class)) {
            rValue = Long.parseLong(value); // String 转为 long 型
        } else if (type.equals(Short.TYPE) || type.equals(Short.class)) {
            rValue = Short.parseShort(value); // String 转为 short 型
        } else if (type.equals(Boolean.TYPE) || type.equals(Boolean.class)) {
            rValue = Boolean.parseBoolean(value); // String 转为 boolean 型
        } else if (type.equals(Float.TYPE) || type.equals(Float.class)) {
            rValue = Float.parseFloat(value); // String 转为 float 型
        } else if (type.equals(Double.TYPE) || type.equals(Double.class)) {
            rValue = Double.parseDouble(value); // String 转为 double 型
        } else if (type.equals(Character.TYPE) || type.equals(Character.class)) {

        } else if (type.equals(Byte.TYPE) || type.equals(Byte.class)) {
            rValue = Byte.parseByte(value); // String 转为 byte 型
        } else if (type.equals(Date.class)) {
            rValue = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse(value);
        } else {
            rValue = value;
        }
        return rValue;
    }
	
	
	public static void main(String[] args) {
		Object[] objArrays = new Object[]{true, 1};
		
		if (objArrays[1].getClass() == Integer.TYPE) {
			System.out.println(" ----------------- ");
		} else {
			System.out.println(" 8888888 ");
		}
		
		
//		Class[] clazzs = new Class[args.length];
//		for (int i = 0; i < args.length; i++) {
//			clazzs[i] = args[i].getClass();
//		}
	}
	
	
	
}
