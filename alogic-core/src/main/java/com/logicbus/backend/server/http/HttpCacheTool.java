package com.logicbus.backend.server.http;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.servlet.http.HttpServletResponse;

import com.anysoft.util.Properties;
import com.anysoft.util.PropertiesConstants;
import com.logicbus.backend.Context;

/**
 * Http的缓存控制
 * 
 * @author yyduan
 * @since 1.6.11.48
 * 
 * @version 1.6.11.50 [20180808 duanyy] <br>
 * - 增加Cache-Enable的头输出,以便前端获取服务的缓存模式 <br>
 */
public class HttpCacheTool {
	protected static SimpleDateFormat dateFormat=new SimpleDateFormat("E, dd MM yyyy HH:mm:ss z",Locale.US);
	protected int cacheMaxAge = Integer.MAX_VALUE;
	
	public HttpCacheTool(Properties p){
		cacheMaxAge = PropertiesConstants.getInt(p, "http.cache.maxage", cacheMaxAge);
	}
	
	public void cacheEnable(HttpServletResponse response){
		Date now = new Date();	
		response.setHeader("Cache-Control", String.format("max-age=%d", cacheMaxAge));
		response.setHeader("Date", dateFormat.format(now));		
		response.setHeader("Cache-Enable", "true");
	}
	
	public void cacheEnable(Context ctx){
		Date now = new Date();	
		ctx.setResponseHeader("Cache-Control", String.format("max-age=%d", cacheMaxAge));	
		ctx.setResponseHeader("Date", dateFormat.format(now));		
		ctx.setResponseHeader("Cache-Enable", "true");
	}
	
	public void cacheDisable(HttpServletResponse response){
		Date now = new Date();		
		response.setHeader("Expires", dateFormat.format(now));
		response.setHeader("Last-Modified", dateFormat.format(now));
		response.setHeader("Cache-Control", "no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");	
		response.setHeader("Cache-Enable", "false");
	}
	
	public void cacheDisable(Context ctx){
		Date now = new Date();		
		ctx.setResponseHeader("Expires", dateFormat.format(now));
		ctx.setResponseHeader("Last-Modified", dateFormat.format(now));
		ctx.setResponseHeader("Cache-Control", "no-cache, must-revalidate");
		ctx.setResponseHeader("Pragma", "no-cache");
		ctx.setResponseHeader("Cache-Enable", "false");
	}	
}
