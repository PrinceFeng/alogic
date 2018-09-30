package com.alogic.oauth.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.alogic.auth.Session;
import com.alogic.auth.SessionManager;
import com.alogic.auth.SessionManagerFactory;
import com.alogic.load.Loader;
import com.alogic.oauth.OAuthConstants;
import com.alogic.oauth.client.loader.FromInner;
import com.anysoft.util.BaseException;
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
import com.anysoft.webloader.FilterConfigProperties;

/**
 * 处理OAuth的过滤器
 * 
 * @author yyduan
 * @since 1.6.11.61
 */
public class OAuthAuthz implements Filter,OAuthConstants,XMLConfigurable,Configurable{
	/**
	 * a logger of slf4j
	 */
	protected final static Logger LOG = LoggerFactory.getLogger(OAuthAuthz.class);
	
	/**
	 * 缺省配置文件
	 */
	protected static final String DEFAULT = "java:///conf/alogic.oauth.client.xml#App";
	
	/**
	 * 缺省的服务id
	 */
	protected String dftServerId = "default";
	
	/**
	 * command的前缀
	 */
	protected String cmdPrefix = "/oauthclient";	
	
	/**
	 * 内部跳转URL的参数
	 */
	protected String returnURL = OAuthConstants.ARGU_RETURNURL;		
	
	/**
	 * 缺省的主页URL
	 */
	protected String mainPage = "";	
	
	protected Loader<OAuthServer> loader = null;
	
	protected String sessionGroup = "$oauth-client";
	
	/**
	 * 正则表达式从OAuthServer回调路径中匹配from
	 */
	protected Pattern pattern = Pattern.compile("/callback/(?<from>[\\w]+)/(?<action>[\\w]+)");	
	
	@Override
	public void configure(Properties props) {
		dftServerId = PropertiesConstants.getString(props, "dftServer",dftServerId);
		cmdPrefix = PropertiesConstants.getString(props, "cmdPrefix",cmdPrefix);
		returnURL = PropertiesConstants.getString(props,"auth.para.url",returnURL);
		mainPage = PropertiesConstants.getString(props,"auth.page.main",mainPage);	
		sessionGroup = PropertiesConstants.getString(props, "oauth.client.group",sessionGroup);	
	}

	@Override
	public void configure(Element e, Properties p) {
		Properties props = new XmlElementProperties(e,p);
		
		Element elem = XmlTools.getFirstElementByPath(e, "servers");
		if (elem != null){
			try {
				Factory<Loader<OAuthServer>> f = new Factory<Loader<OAuthServer>>();
				loader = f.newInstance(elem, props, "loader",FromInner.class.getName());
			}catch (Exception ex){
				LOG.error("Can not create loader with " + XmlTools.node2String(elem));
				LOG.error(ExceptionUtils.getStackTrace(ex));
			}
		}		
		
		configure(props);
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		FilterConfigProperties props = new FilterConfigProperties(filterConfig);
		String master = PropertiesConstants.getString(props, "oauth.client.master", DEFAULT);
		String secondary = PropertiesConstants.getString(props, "oauth.client.secondary", DEFAULT);
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
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpReq = (HttpServletRequest)request;
		HttpServletResponse httpResp = (HttpServletResponse)response;
		SessionManager sm = SessionManagerFactory.getDefault();
		Session session = sm.getSession(httpReq,httpResp,true);
		
		String cmd = getCommand(httpReq.getRequestURI());
		LOG.info("cmd = " + cmd);
		if (StringUtils.isNotEmpty(cmd)){
			if (cmd.startsWith("/callback")){
				Matcher matcher = pattern.matcher(cmd);
				if (matcher.find()){
					String serverId = matcher.group("from");
					OAuthServer server = this.getServer(serverId);
					if (server == null){
						httpResp.sendError(E404,"core.e1000:Unsupported oauth server,id=" + serverId);
						return;
					}
					
					String action = matcher.group("action");
					if (action.equals("bind")){
						server.doBindCallback(httpReq, httpResp, sm, session);
					}else{
						server.doLoginCallback(httpReq, httpResp, sm, session);
					}	
					
					//验证通过后,重定向到指定的地址
					String redirect = session.hGet(sessionGroup,returnURL,mainPage);
					if (StringUtils.isNotEmpty(redirect)){
						session.hDel(sessionGroup, returnURL);
						httpResp.sendRedirect(redirect);	
					}	
					return ;
				}else{
					httpResp.sendError(E404,"core.e1000:Unsupported command:" + cmd);
					return ;
				}
			}		
			
			if (cmd.startsWith("/login")){
				//由本地地址调用，跳转登录
				String serverId = getParameter(httpReq, OAuthConstants.ARGU_FROM, dftServerId);
				OAuthServer server = this.getServer(serverId);
				if (server == null){
					httpResp.sendError(E404,"core.e1000:Unsupported oauth server,id=" + serverId);
					return;
				}	
		
				//将请求页面保存在Session中，准备验证之后跳转
				String redirectURL = getParameter(httpReq, returnURL, "");
				if (StringUtils.isNotEmpty(redirectURL)){
					session.hSet(sessionGroup, returnURL, redirectURL, true);					
				}
				session.hSet(sessionGroup, OAuthConstants.ARGU_FROM, serverId, true);
				
				try {
					server.doLoginRequest(httpReq, httpResp, sm, session);
				}catch (BaseException ex){
					httpResp.sendError(E404,String.format("%s:%s",ex.getCode(),ex.getMessage()));
				}
				return ;
			}
			
			if (cmd.startsWith("/bind")){
				//由本地地址调用，跳转登录
				String serverId = getParameter(httpReq, OAuthConstants.ARGU_FROM, dftServerId);
				OAuthServer server = this.getServer(serverId);
				if (server == null){
					httpResp.sendError(E404,"core.e1000:Unsupported oauth server,id=" + serverId);
					return;
				}	
		
				//将请求页面保存在Session中，准备验证之后跳转
				String redirectURL = getParameter(httpReq, returnURL, "");
				if (StringUtils.isNotEmpty(redirectURL)){
					session.hSet(sessionGroup, returnURL, redirectURL, true);					
				}
				session.hSet(sessionGroup, OAuthConstants.ARGU_FROM, serverId, true);
				
				try {
					server.doBindRequest(httpReq, httpResp, sm, session);
				}catch (BaseException ex){
					httpResp.sendError(E404,String.format("%s:%s",ex.getCode(),ex.getMessage()));
				}
				return ;
			}			
		}
		if (session.isLoggedIn()){
			//已经登录
			chain.doFilter(request, response);
			return ;
		}else{
			String serverId = getParameter(httpReq, OAuthConstants.ARGU_FROM, dftServerId);
			OAuthServer server = this.getServer(serverId);
			if (server == null){
				httpResp.sendError(E404,"core.e1000:Unsupported oauth server,id=" + serverId);
				return;
			}	
	
			//将请求页面保存在Session中，准备验证之后跳转
			String requestUrl = httpReq.getRequestURI();
			String query = httpReq.getQueryString();		
			if (StringUtils.isNotEmpty(query)){
				requestUrl = requestUrl + "?" + query;
			}		
			
			session.hSet(sessionGroup, returnURL, requestUrl, true);	
			session.hSet(sessionGroup, OAuthConstants.ARGU_FROM, serverId, true);
			
			try {
				server.doLoginRequest(httpReq,httpResp,sm,session);
			}catch (BaseException ex){
				httpResp.sendError(E404,String.format("%s:%s",ex.getCode(),ex.getMessage()));
			}
			return ;
		}		
	}

	@Override
	public void destroy() {
		// nothig to do
	}
	
	protected OAuthServer getServer(String id){
		return loader.load(id, true);
	}
	
	/**
	 * 通过URI计算出当前的command
	 * @param uri URI
	 * @return cmd
	 */
	protected String getCommand(String uri){
		if (uri.startsWith(cmdPrefix)){
			return uri.substring(cmdPrefix.length());
		}else{
			return "";
		}
	}
	
	/**
	 * 从Request中获取指定的参数
	 * @param request HttpServletRequest
	 * @param id 参数id
	 * @param dftValue 缺省值，当参数不存在时，返回
	 * @return　参数值，如果参数不存在，返回缺省值
	 */
	protected String getParameter(HttpServletRequest request,String id,String dftValue){
		String value = request.getParameter(id);
		return StringUtils.isEmpty(value)?dftValue:value;
	}	

}
