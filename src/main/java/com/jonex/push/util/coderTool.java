package com.jonex.push.util;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * 对数据解码编码的工具类
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class coderTool {

	private static Charset charset = Charset.forName("utf-8");
	
	/**
	 * 对字符串进行byte编码
	 * 
	 * @param str
	 * @return ByteBuffer
	 * @author zer0
	 * @version 1.0
	 * @date 2015-2-23
	 */
	public static ByteBuffer encode(String str) {   
		return charset.encode(str);   
	}
	
	/**
	 * 对bytebuffer进行解码，解码成字符串
	 * 
	 * @param byteBuffer
	 * @return String
	 * @author zer0
	 * @version 1.0
	 * @date 2015-2-23
	 */
	public static String decode(ByteBuffer byteBuffer){
		return charset.decode(byteBuffer).toString();
	}
	
}
