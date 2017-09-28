package com.jonex.push.subscribe;

/**
 *  此类用于存储每个Topic解析出来的订阅（Topic：country/china/tianjin）
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class Token {

	static final Token MULTI = new Token("#");
	static final Token SINGLE = new Token("+");
	static final Token EMPTY = new Token("");
	String name;
	
	Token(String name){
		this.name = name;
	}
	
}
