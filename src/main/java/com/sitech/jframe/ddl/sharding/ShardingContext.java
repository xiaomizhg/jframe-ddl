package com.sitech.jframe.ddl.sharding;

public class ShardingContext {
	
	private static ShardingContext sc = new ShardingContext();
	
	private ISharding sharding;
	
	private ShardingContext() {
		this.sharding = new NoSharding();
	}
	
	public static ShardingContext getInstance() {
		return sc;
	}

	
	public ISharding getSharding() {
		return sharding;
	}

	public void setSharding(ISharding sharding) {
		this.sharding = sharding;
	}
	
	

}
