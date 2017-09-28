package com.jonex.push.job;

import com.jonex.push.protocol.ProtocolProcess;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 *  Publish消息重发事件需要做的工作，即重发消息到对应的clientID
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class RePubRelJob implements Job{
	
	public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
		//取出参数，参数为ProtocolProcess，调用此类的函数
		JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
		ProtocolProcess process = (ProtocolProcess) dataMap.get("ProtocolProcess");
		String pubRelKey = (String) dataMap.get("pubRelKey");
		process.reUnKnowPubRelMessage(pubRelKey);
	}
	
}
