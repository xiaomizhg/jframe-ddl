package com.sitech.jframe.ddl.datasource.lb;

import java.util.Random;

import javax.sql.DataSource;

/**
 * 从一个数组中随机获取一个 DataSource
 * @author zhangsf
 *
 */
public class DefaultDataSourceSelector extends AbstractDataSourceSelector {
	private static Random r = new Random();
	

	@Override
	public DataSource doSelector(String lookupKey) {
		
		DataSource[] dataSourceArray = super.getDataSourceArray(lookupKey);
		if (dataSourceArray != null && dataSourceArray.length == 1) {
			return dataSourceArray[0];
		} else if (dataSourceArray != null && dataSourceArray.length > 1) {
			int index = r.nextInt(dataSourceArray.length);
			return dataSourceArray[index];
		}
		return null;
	}
	
	public static void  main(String args[]) {
		System.out.println(r.nextInt(1));
	}

}
