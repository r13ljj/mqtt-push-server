package com.jonex.push.store;

/**
 *  身份验证接口
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public interface IAuthenticator {

	/**
	 * 校验用户名和密码是否正确
	 * @param username
	 * @param password
	 * @return boolean
	 */
	boolean checkValid(String username, String password);
	
}
