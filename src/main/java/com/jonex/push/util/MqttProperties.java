package com.jonex.push.util;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Java Properties属性文件操作类
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class MqttProperties {
	
	private final static Logger Log = Logger.getLogger(MqttProperties.class);
	
	private static Properties props = new Properties();
	//配置文件路径
	private static final String CONFIG_FILE = System.getProperty("user.dir") + "/mqttpushserver/resource/mqtt.properties";
	
	static{
		loadProperties(CONFIG_FILE);
	}
	
	/**
	 * 加载属性文件
	 * 
	 * @param propertyFilePath
	 */
	private static void loadProperties(String propertyFilePath){
		try {
			FileInputStream in = new FileInputStream(propertyFilePath);
			props = new Properties();
			props.load(in);
		} catch (IOException e) {
			Log.error("属性文件读取错误");
			e.printStackTrace();
		}
	}

	/**
	 * 从指定的键取得对应的值
	 * 
	 * @param key
	 * @return String
	 */
	public static String getProperty(String key){
		return props.getProperty(key);
	}
	
	/**
	 *  从指定的键取得整数
	 * 
	 * @param key
	 * @return Integer
	 */
	public static Integer getPropertyToInt(String key){
		String str = props.getProperty(key);
		if(StringTool.isBlank(str.trim())){
			return null;
		}
		return Integer.valueOf(str.trim()); 
	}
}
