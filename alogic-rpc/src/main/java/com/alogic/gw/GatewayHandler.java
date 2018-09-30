package com.alogic.gw;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.alogic.gw.loader.FromInner;
import com.alogic.load.Loader;
import com.alogic.remote.Client;
import com.alogic.remote.ClientFactory;
import com.alogic.remote.HttpConstants;
import com.alogic.remote.Request;
import com.alogic.remote.Response;
import com.alogic.remote.httpclient.HttpClient;
import com.alogic.tracer.Tool;
import com.alogic.tracer.TraceContext;
import com.anysoft.util.Configurable;
import com.anysoft.util.Factory;
import com.anysoft.util.IOTools;
import com.anysoft.util.Properties;
import com.anysoft.util.PropertiesConstants;
import com.anysoft.util.Settings;
import com.anysoft.util.XMLConfigurable;
import com.anysoft.util.XmlElementProperties;
import com.anysoft.util.XmlTools;
import com.anysoft.util.resource.ResourceFactory;
import com.anysoft.webloader.ServletConfigProperties;
import com.anysoft.webloader.ServletHandler;
import com.logicbus.backend.AccessController;
import com.logicbus.backend.Context;
import com.logicbus.backend.server.http.HttpCacheTool;
import com.logicbus.backend.server.http.HttpContext;
import com.logicbus.models.catalog.Path;

/**
 * 网关的ServletHandler
 * 
 * @author yyduan
 * @since 1.6.11.4
 * 
 * @version 1.6.11.16 [20180207 duanyy] <br>
 * - 优化ip的forworded处理 <br>
 * 
 * @version 1.6.11.23 [20180320 duanyy] <br>
 * - 增加本地服务的转调功能 <br>
 * 
 * @version 1.6.11.50 [20180808 duanyy] <br>
 * - 增加form数据的拦截模式 <br>
 * - 优化Gateway的缓存处理 <br>
 */
public class GatewayHandler implements ServletHandler,XMLConfigurable,Configurable{
	/**
	 * a logger of slf4j
	 */
	protected static final Logger LOG = LoggerFactory.getLogger(GatewayHandler.class);
	
	/**
	 * 缺省gateway配置文件
	 */
	protected static final String DEFAULT = "java:///com/alogic/gw/gateway.xml#" + GatewayHandler.class.getName();
	
	/**
	 * remote client
	 */
	protected Client client = null;
	
	/**
	 * 缺省编码
	 */
	protected String encoding = "utf-8";
	
	/**
	 * 是否传递真实地址信息
	 */
	protected boolean forwarded = true;
	
	/**
	 * 传递真实地址信息时的Http头
	 */
	protected String forwardedHeader = "X-Forwarded-For";
	
	/**
	 * 传递真实ip的http头
	 */
	protected String readIpHeader = "X-Real-IP";
	
	/**
	 * 是否开启tlog
	 */
	protected boolean tracerEnable = false;
	
	/**
	 * 后端服务地址前缀
	 */
	protected String proxyPath = "/services/";	
	
	/**
	 * Access-Control-Allow-Origin
	 */
	protected String defaultAllowOrigin = "*";
	
	/**
	 * 允许的方法
	 */
	protected String methodAllow = "GET,PUT,POST";
	
	/**
	 * 是否支持cors
	 */
	protected boolean corsSupport = true;
	
	/**
	 * 访问控制器
	 */
	protected AccessController ac = null;
	
	/**
	 * 是否启用访问控制
	 */
	protected boolean acmEnable = true;	
	
	/**
	 * 服务规格描述loader
	 */
	protected Loader<OpenServiceDescription> descs = null;
	
	/**
	 * 缺省的服务
	 */
	protected OpenServiceDescription dft = null;
	
	/**
	 * 本地服务转调
	 */
	protected RequestDispatcher dispatcher = null;
	
	/**
	 * 自身的应用id
	 */
	protected String selfApp = "${server.app}";
	
	/**
	 * form拦截模式
	 */
	protected boolean interceptMode = false;
	
	protected HttpCacheTool cacheTool = null;
	
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		ServletConfigProperties props = new ServletConfigProperties(servletConfig);
		
		String master = PropertiesConstants.getString(props, "gateway.master", DEFAULT);
		String secondary = PropertiesConstants.getString(props, "gateway.secondary", DEFAULT);
		ResourceFactory rf = Settings.getResourceFactory();

		InputStream in = null;
		try {
			in = rf.load(master, secondary, null);
			Document doc = XmlTools.loadFromInputStream(in);
			if (doc != null){
				configure(doc.getDocumentElement(), props);
			}
		}catch (Exception ex){
			LOG.error("Can not init gateway with file : " + master);
		}finally{
			IOTools.close(in);
		}
		
		dispatcher = servletConfig.getServletContext().getNamedDispatcher(
				PropertiesConstants.getString(props, "gateway.self.servlet", "MessageRouter")
			);
	}
	
	@Override
	public void configure(Properties props) {
		encoding = PropertiesConstants.getString(props, "http.encoding", encoding);	
		defaultAllowOrigin = PropertiesConstants.getString(props,"http.alloworigin",defaultAllowOrigin);
		corsSupport = PropertiesConstants.getBoolean(props, "http.cors", corsSupport);
		methodAllow = PropertiesConstants.getString(props, "http.method.allow", methodAllow);
		forwarded = PropertiesConstants.getBoolean(props, "http.forwarded", forwarded);
		forwardedHeader = PropertiesConstants.getString(props, "http.forwarded.header", forwardedHeader);
		readIpHeader = PropertiesConstants.getString(props, "http.realip.header", readIpHeader);
		
		tracerEnable = PropertiesConstants.getBoolean(props, "gateway.tlog", tracerEnable);
		proxyPath = PropertiesConstants.getString(props, "gateway.path", proxyPath);
		acmEnable = PropertiesConstants.getBoolean(props, "gateway.acm", acmEnable);	
		selfApp = PropertiesConstants.getString(props, "gateway.self.app", selfApp);
		interceptMode = PropertiesConstants.getBoolean(props, "gateway.intercept", interceptMode);		
		cacheTool = Settings.get().getToolkit(HttpCacheTool.class);
		//采用统一的访问控制器
		ac = (AccessController) Settings.get().get("accessController");
	}
	
	@Override
	public void configure(Element e, Properties p) {
		Properties props = new XmlElementProperties(e,p);

		//从子节点中找client配置
		Element clientElem = XmlTools.getFirstElementByPath(e, "client");
		if (clientElem != null){
			client = ClientFactory.loadFrom(clientElem, "module", props);
		}
		
		if (client == null){
			//如果没有配置client节点，通过gw.client.master参数指定的配置文件中配置
			String master = PropertiesConstants.getString(props, "gateway.client.master", "${app.proxy.master}");
			String secondary = PropertiesConstants.getString(props, "gateway.client.secondary", "${app.proxy.secondary}");
			client = ClientFactory.loadFrom(master, secondary, Settings.getResourceFactory());
			
			if (client == null){
				client = new HttpClient();
				client.configure(e, p);
				LOG.info(String.format("Using default remote client:%s",client.getClass().getName()));
			}
		}
		
		Element descsElem = XmlTools.getFirstElementByPath(e, "api");
		if (descsElem != null){
			try {
				Factory<Loader<OpenServiceDescription>> f = new Factory<Loader<OpenServiceDescription>>();
				descs = f.newInstance(descsElem, props, "loader",FromInner.class.getName());
				dft = descs.load(PropertiesConstants.getString(props,"gateway.service.dft","default"), true);
			}catch (Exception ex){
				LOG.error("Can not create loader with " + XmlTools.node2String(descsElem));
				LOG.error(ExceptionUtils.getStackTrace(ex));
			}
		}
		
		configure(props);
	}

	/**
	 * 根据服务id来查找服务描述
	 * @param id 服务id
	 * @return 服务描述
	 */
	protected OpenServiceDescription loadServiceDescription(String id){
		OpenServiceDescription sd = descs == null ? null : descs.load(id, true);
		return sd == null ? dft : sd;
	}
	
	@Override
	public void doService(HttpServletRequest request,
			HttpServletResponse response, String method)
			throws ServletException, IOException {
		if (method.equals("options")){
			response.setHeader("Allow", methodAllow);
		}else{	
			//获取开放服务id
			String openId = request.getPathInfo();
			if (openId == null || openId.length() <= 1) {
				response.sendError(HttpConstants.E404, "I do not know service id.");
			} else {
				openId = openId.substring(1);
			}
			if (StringUtils.isEmpty(openId)) {
				response.sendError(HttpConstants.E404, "I do not know service id.");
				return;
			}

			OpenServiceDescription sd = loadServiceDescription(openId);
			if (sd == null){
				if (dispatcher != null){
					//当找不到服务定义的时候，如果dispatcher不为空，转调dispatcher
					dispatcher.forward(request, response);
					return ;
				}
				response.sendError(HttpConstants.E404, String.format("Service %s does not exist", openId));
				return ;
			}
			
			String backendApp = sd.getBackendApp();
			if (backendApp.equals(selfApp) && dispatcher != null){
				//当后端应用和本应用一致时，转调
				dispatcher.forward(request, response);	
				return ;
			}
			
			if (corsSupport){
				String origin = request.getHeader("Origin");
				response.setHeader("Access-Control-Allow-Origin", StringUtils.isEmpty(origin) ? defaultAllowOrigin : origin);
				response.setHeader("Access-Control-Allow-Credentials", "true");
			}			
			
			Context ctx = new HttpContext(request, response, encoding,interceptMode);
			ctx.SetValue("$app", backendApp);
			
			TraceContext tc = null;
			if (tracerEnable) {
				tc = Tool.start(ctx.getGlobalSerial(), ctx.getGlobalSerialOrder());
			}
			
			Request req = client.build(method);
			
			boolean ok = true;
			String reason = String.format("[%s]", ctx.getClientIp());
			int contentLength = 0;
			String sessionId = "";
			Path id = new Path(openId);
			String query = request.getQueryString();
			
			try {
				int priority = 0;			
				if (acmEnable && null != ac){
					sessionId = ac.createSessionId(id, sd, ctx);
					priority = ac.accessStart(sessionId,id, sd, ctx);
					if (priority < 0){
						ok = false;
						reason = reason + "Unauthorized Access:" + ctx.getClientIp() + ",url:" + ctx.getRequestURI();
						LOG.info("Unauthorized Access:" + ctx.getClientIp() + ",url:" + ctx.getRequestURI());
						response.sendError(HttpConstants.E404, "Permission denied!service id: "+ id);
						return;
					}
				}
				
				TraceContext child = tc == null ? null:tc.newChild();
				if (child != null){
					req.setHeader("GlobalSerial", child.sn());
					req.setHeader("GlobalSerialOrder", child.order());
				}
				
				String endpointPath = proxyPath + sd.getBackendPath() + "?openId=" + URLEncoder.encode(openId,encoding);
				if (StringUtils.isNotEmpty(query)){
					endpointPath += "&" + query;
				}
											
				doRequest(request,req,ctx);				
				Response resp = doExecute(endpointPath,request,req,ctx);	
				doResponse(response,resp,ctx);
				
				reason = reason + resp.getStatusCode() + resp.getReasonPhrase();							
			}catch (Exception ex){
				ok = false;
				reason = reason + ExceptionUtils.getStackTrace(ex).substring(0,128);
				LOG.error("Error occurs when calling.",ex);
				response.sendError(HttpConstants.E404, ex.getMessage());
			}finally{
				if (acmEnable && ac != null){
					ac.accessEnd(sessionId,id, sd, ctx);
				}	
				IOTools.close(req);
				if (tracerEnable){
					Tool.end(tc, "Native", openId, ok ?"OK":"FAILED", reason,query, contentLength);
				}			
			}
		}
	}

	/**
	 * 进行Reponse操作
	 * @param response 南向Response
	 * @param resp 北向Response
	 * @param ctx 上下文
	 * @throws IOException
	 */
	protected void doResponse(HttpServletResponse response, Response resp,Context ctx) throws IOException {
		byte[] result = resp.asBytes();
		response.setContentLength(result.length);
		String contentType = resp.getContentType();
		if (StringUtils.isNotEmpty(contentType)){
			response.setContentType(contentType);
		}
		
		if (BooleanUtils.toBoolean(resp.getHeader("Cache-Enable", "false"))){
			cacheTool.cacheEnable(response);
		}else{
			cacheTool.cacheDisable(response);
		}
		
		response.setStatus(resp.getStatusCode());
		response.getOutputStream().write(result);
	}

	/**
	 * 进行Request操作
	 * @param request 南向http请求
	 * @param req 北向http请求
	 * @param ctx 上下文
	 * @throws IOException 
	 */
	protected void doRequest(HttpServletRequest request, Request req,Context ctx)  throws IOException{
		String reqContextType = request.getContentType();
		if (StringUtils.isNotEmpty(reqContextType)){
			req.setHeader(HttpConstants.CONTENT_TYPE, reqContextType);
		}					
		if (forwarded){
			String forwarded = request.getHeader(forwardedHeader);
			if (StringUtils.isNotEmpty(forwarded)){
				forwarded += "," + request.getRemoteHost();
			}else{
				forwarded = request.getRemoteHost();
			}
			req.setHeader(forwardedHeader, forwarded);
			req.setHeader(readIpHeader, request.getRemoteHost());
		}
		
		byte[] data = ctx.getRequestRaw();
		if (data != null){
			req.setBody(data);
		}else{
			req.setBody(request.getInputStream());
		}
	}
	
	/**
	 * 进行远程调用执行操作
	 * @param endpointPath 最终的URL地址
	 * @param request 南向http请求
	 * @param req 北向http请求
	 * @param ctx 上下文
	 * @return 北向Response
	 * @throws IOException
	 */
	protected Response doExecute(String endpointPath,HttpServletRequest request, Request req,Context ctx) throws IOException{		
		return req.execute(endpointPath, ctx.getGlobalSerial(), ctx);
	}

	@Override
	public void destroy() {
		// nothing to do
	}


}
