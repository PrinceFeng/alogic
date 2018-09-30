package com.alogic.load;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.anysoft.util.Configurable;
import com.anysoft.util.Factory;
import com.anysoft.util.IOTools;
import com.anysoft.util.JsonTools;
import com.anysoft.util.Properties;
import com.anysoft.util.PropertiesConstants;
import com.anysoft.util.Settings;
import com.anysoft.util.XmlElementProperties;
import com.anysoft.util.Reportable;
import com.anysoft.util.Watcher;
import com.anysoft.util.XMLConfigurable;
import com.anysoft.util.XmlTools;
import com.anysoft.util.resource.ResourceFactory;

/**
 * 通用装载框架
 * 
 * @author duanyy
 * @version 1.6.5.13 [20160612 duanyy] <br>
 * - 增加对象过期判断功能 <br>
 * 
 * @version 1.6.7.9 [20170201 duanyy] <br>
 * - 采用SLF4j日志框架输出日志 <br>
 * 
 * @version 1.6.10.6 [20171114 duanyy] <br>
 * - 优化日志输出  <br>
 * 
 * @version 1.6.11.4 [20171222 duanyy] <br>
 * - 增加Hot实现 <br>
 * 
 * @version 1.6.11.15 [20180206 duanyy] <br>
 * - 加载sink时增加scope支持 <br>
 * 
 * @version 1.6.11.45 [duanyy 20180722] <br>
 * - Sinkable实现增加nocache模式;
 */
public interface Loader<O extends Loadable> extends Configurable,XMLConfigurable,Reportable{
	
	/**
	 * 按照id装载指定的对象
	 * 
	 * @param id 对象id
	 * @param cacheAllowed 是否允许缓存
	 * @return 对象实例
	 */
	public O load(String id,boolean cacheAllowed);
	
	/**
	 * 注册监听器
	 * 
	 * @param watcher 监听器
	 */
	public void addWatcher(Watcher<O> watcher);
	
	/**
	 * 注销监听器
	 * @param watcher 监听器
	 */
	public void removeWatcher(Watcher<O> watcher);
	
	/**
	 * 虚基类 
	 * 
	 * @author duanyy
	 *
	 * @param <O>
	 */
	public abstract static class Abstract<O extends Loadable> implements Loader<O>{
		/**
		 * a logger of log4j
		 */
		protected static final Logger LOG = LoggerFactory.getLogger(Loader.class);
		
		/**
		 * 对象有效期
		 */
		protected long ttl = 0;
		
		/**
		 * 获取对象有效期
		 * @return ttl
		 */
		public long getTTL(){
			return ttl;
		}
		
		@Override
		public void configure(Properties p) {
			ttl = PropertiesConstants.getLong(p,"ttl", ttl);
		}
		
		@Override
		public void configure(Element e, Properties p) {
			Properties props = new XmlElementProperties(e,p);
			configure(props);
		}

		@Override
		public void report(Element xml) {
			if (xml != null){
				XmlTools.setString(xml,"module",getClass().getName());
			}
		}

		@Override
		public void report(Map<String, Object> json) {
			if (json != null){
				JsonTools.setString(json,"module",getClass().getName());
			}
		}

		@Override
		public void addWatcher(Watcher<O> watcher) {
			// nothing to do
		}
		
		@Override
		public void removeWatcher(Watcher<O> watcher) {
			// nothing to do
		}
		
		/**
		 * 指定的Loadable对象是否过期
		 * @param o 对象
		 * @return 是否过期
		 */
		protected boolean isExpired(O o){
			if (ttl > 0){
				return o != null && System.currentTimeMillis() - o.getTimestamp() > ttl;
			}else{
				return o != null && o.isExpired();
			}
		}
	}
	
	/**
	 * Sinkable
	 * @author duanyy
	 *
	 */
	public abstract static class Sinkable<O extends Loadable> extends Abstract<O> {
		protected List<Loader<O>> loaders = new ArrayList<Loader<O>>();
		
		/**
		 * 本身不缓存
		 */
		private boolean noCache = false;
		
		/**
		 * 是否本身不缓存数据
		 * @return 如果为true,则本身不缓存数据
		 */
		protected boolean noCache(){
			return noCache;
		}
		
		@Override
		public void configure(Properties p){
			super.configure(p);			
			noCache = PropertiesConstants.getBoolean(p,"noCache", noCache);
		}
		
		@Override
		public void configure(Element e, Properties p) {
			Properties props = new XmlElementProperties(e,p);
			configure(props);
			
			NodeList nodeList = XmlTools.getNodeListByPath(e, getSinkTag());
			Factory<Loader<O>> factory = new Factory<Loader<O>>();
			String scope = PropertiesConstants.getString(p, "ketty.scope", "runtime");
			
			for (int i = 0 ;i < nodeList.getLength() ; i ++){
				Node n = nodeList.item(i);
				
				if (Node.ELEMENT_NODE != n.getNodeType()){
					continue;
				}
				
				Element elem = (Element)n;
				
				String itemScope = XmlTools.getString(elem, "scope", "");
				if (StringUtils.isNotEmpty(itemScope) && !itemScope.equals(scope)){
					continue;
				}
				
				try {
					Loader<O> loader = factory.newInstance(elem, props, "module");
					if (loader != null){
						loaders.add(loader);
					}
				}catch (Exception ex){
					LOG.error("Can not create loader from element:" + XmlTools.node2String(elem));
					LOG.error(ExceptionUtils.getStackTrace(ex));
				}
			}
			
		}
		
		/**
		 * 获取Sink的tag名
		 * @return tag名
		 */
		protected String getSinkTag(){
			return "sink";
		}
		
		@Override
		public O load(String id, boolean cacheAllowed) {
			O found = noCache()? null:loadFromSelf(id,cacheAllowed);
			if (found == null){
				found = loadFromSink(id,cacheAllowed);
			}
			
			return found;
		}
	
		protected O loadFromSink(String id,boolean cacheAllowed){
			for (Loader<O> l:loaders){
				O found = l.load(id, cacheAllowed);
				if (found != null){
					return found;
				}
			}
			return null;
		}
		
		@Override
		public void report(Element xml) {
			if (xml != null){
				super.report(xml);
				
				if (!loaders.isEmpty()){
					Document doc = xml.getOwnerDocument();
					for (Loader<O> l:loaders){
						Element elem = doc.createElement("sink");
						l.report(elem);
						xml.appendChild(elem);
					}
				}
			}
		}

		@Override
		public void report(Map<String, Object> json) {
			if (json != null){
				super.report(json);
				
				if (!loaders.isEmpty()){
					List<Object> list = new ArrayList<Object>();
					for (Loader<O> l:loaders){
						Map<String,Object> map = new HashMap<String,Object>();
						l.report(map);
						list.add(map);
					}
					json.put("sink", list);
				}
			}
		}		
		
		@Override
		public void addWatcher(Watcher<O> watcher) {
			for (Loader<O> l:loaders){
				l.addWatcher(watcher);
			}
		}
		
		@Override
		public void removeWatcher(Watcher<O> watcher) {
			for (Loader<O> l:loaders){
				l.removeWatcher(watcher);
			}
		}
		
		protected abstract O loadFromSelf(String id,boolean cacheAllowed);
	}
	
	/**
	 * 缓存过的
	 * @author duanyy
	 *
	 * @param <O>
	 */
	public static class Cached<O extends Loadable> extends Sinkable<O> implements Watcher<O>{
		/**
		 * 缓存的对象
		 */
		private Map<String,O> cachedObjects = new ConcurrentHashMap<String,O>();
		
		/**
		 * 监听器
		 */
		private List<Watcher<O>> watchers = new ArrayList<Watcher<O>>();
		
		@Override
		public void configure(Element e, Properties p) {
			super.configure(e, p);
			
			for (Loader<O> l:loaders){
				l.addWatcher(this);
			}
		}
		
		@Override
		public O load(String id, boolean cacheAllowed) {
			if (noCache()){
				return loadFromSink(id,cacheAllowed);
			}else{
				O found = loadFromSelf(id,cacheAllowed);
				if (found == null){
					synchronized (this){
						found = loadFromSelf(id,cacheAllowed);
						if (found == null){
							found = loadFromSink(id,cacheAllowed);
							if (found != null){
								cachedObjects.put(id, found);
							}
						}
					}
				}
				
				return found;
			}
		}
		
		@Override
		protected O loadFromSelf(String id, boolean cacheAllowed) {
			O found = null;
			if (cacheAllowed){
				found = cachedObjects.get(id);
				if (isExpired(found)){
					cachedObjects.remove(id);
					found = null;
				}
			}
			return found;
		}
		
		@Override
		public void addWatcher(Watcher<O> watcher) {
			for (Loader<O> l:loaders){
				l.addWatcher(watcher);
			}
			watchers.add(watcher);
		}
		
		@Override
		public void removeWatcher(Watcher<O> watcher) {
			for (Loader<O> l:loaders){
				l.removeWatcher(watcher);
			}
			watchers.remove(watcher);
		}

		@Override
		public void added(String id, O data) {
			cachedObjects.remove(id);
			for (Watcher<O> w:watchers){
				w.added(id, data);
			}
		}

		@Override
		public void removed(String id, O data) {
			cachedObjects.remove(id);
			for (Watcher<O> w:watchers){
				w.removed(id, data);
			}
		}

		@Override
		public void changed(String id, O data) {
			cachedObjects.remove(id);
			for (Watcher<O> w:watchers){
				w.changed(id, data);
			}
		}

		@Override
		public void allChanged() {
			cachedObjects.clear();
			for (Watcher<O> w:watchers){
				w.allChanged();
			}
		}
	}
	
	/**
	 * 容器
	 * 
	 * @author duanyy
	 *
	 * @param <O>
	 */
	public static class Container<O extends Loadable> extends Sinkable<O>{

		private Map<String,O> objects = new ConcurrentHashMap<String,O>();
	
		/**
		 * 向缓存加入对象
		 * @param id 对象id
		 * @param o 对象
		 */
		public void add(String id,O o){
			objects.put(id, o);
		}
		
		/**
		 * 从缓存中删除对象
		 * @param id 对象
		 */
		public void remove(String id){
			objects.remove(id);
		}
		
		/**
		 * 清楚缓存所有对象
		 */
		public void clear(){
			objects.clear();
		}
		
		/**
		 * 获取缓存中的对象
		 * @param id 对象id
		 * @return 对象实例
		 */
		public O get(String id){
			return objects.get(id);
		}		
		
		@Override
		protected O loadFromSelf(String id, boolean cacheAllowed) {
			return objects.get(id);
		}	
	}
	
	/**
	 * 基于XML配置的容器
	 * 
	 * @author duanyy
	 *
	 * @param <O>
	 */
	public abstract static class XmlResource<O extends Loadable>  extends Container<O>{
		protected abstract String getObjectXmlTag();
		protected abstract String getObjectDftClass();
		
		@Override
		public void configure(Element root, Properties p) {
			super.configure(root, p);
			
			Properties props = new XmlElementProperties(root,p);
			loadFromElement(root,props);
		}		
		
		protected void loadFromElement(Element root,Properties p){
			NodeList nodeList = XmlTools.getNodeListByPath(root, getObjectXmlTag());
			Factory<O> factory = new Factory<O>();
			
			for (int i = 0 ;i < nodeList.getLength() ; i ++){
				Node n = nodeList.item(i);
				
				if (Node.ELEMENT_NODE != n.getNodeType()){
					continue;
				}
				
				Element e = (Element)n;
				O instance = factory.newInstance(e, p, "module", getObjectDftClass());				
				if (StringUtils.isNotEmpty(instance.getId())){
					add(instance.getId(), instance);
				}
			}
		}
	}
	
	/**
	 * 管理器
	 * 
	 * @author duanyy
	 *
	 * @param <O>
	 */
	public abstract static class Manager<O extends Loadable>{
		/**
		 * a logger of log4j
		 */
		protected static final Logger LOG = LoggerFactory.getLogger(Manager.class);
		
		/**
		 * 获取缺省的实现类名
		 * @return 实现类名
		 */
		protected abstract String getDefaultClass();
		
		/**
		 * 从xml配置节点上装入Loader
		 * 
		 * @param root xml配置节点
		 * @param moduleAttr 类名的字段名
		 * @param p 环境变量
		 * @return Loader实例
		 */
		public Loader<O> loadFrom(Element root,String moduleAttr,Properties p) {
			Loader<O> loader = null;		
			try {
				Factory<Loader<O>> f = new Factory<Loader<O>>();
				loader = f.newInstance(root, p, moduleAttr, getDefaultClass());
			}catch (Exception ex){
				LOG.error(String.format("Can not remote loader with %s", XmlTools.node2String(root)));
			}
			return loader;
		}
		
		/**
		 * 从xml配置节点上装入Loader
		 * 
		 * @param root xml配置节点
		 * @param p 环境变量集
		 * @return Loader实例
		 */
		public Loader<O> loadFrom(Element root,Properties p){
			return loadFrom(root,"module",p);
		}
		
		/**
		 * 从xml文档的根节点装入loader
		 * @param doc xml文档
		 * @param p 环境变量集
		 * @return Loader实例
		 */
		public Loader<O> loadFrom(Document doc,Properties p){
			return loadFrom(doc.getDocumentElement(),p);
		}
		
		/**
		 * 从配置文件的路径中装入
		 * @param master 主路径
		 * @param secondary 备用路径
		 * @return Loader实例
		 */
		public Loader<O> loadFrom(String master,String secondary){
			Loader<O> loader = null;
			ResourceFactory rf = Settings.getResourceFactory();
			InputStream in = null;
			try {
				in = rf.load(master, secondary, null);
				Document doc = XmlTools.loadFromInputStream(in);
				loader = loadFrom(doc,Settings.get());
			} catch (Exception ex) {
				LOG.error("Error occurs when load xml file,source=" + master);
				LOG.error(ExceptionUtils.getStackTrace(ex));
			} finally {
				IOTools.closeStream(in);
			}
			return loader;
		}
		
		/**
		 * 从配置文件的路径中装入
		 * @param src 主路径
		 * @return Loader实例
		 */
		public Loader<O> loadFrom(String src){
			Loader<O> loader = null;
			ResourceFactory rf = Settings.getResourceFactory();
			InputStream in = null;
			try {
				in = rf.load(src, null, null);
				Document doc = XmlTools.loadFromInputStream(in);
				loader = loadFrom(doc,Settings.get());
			} catch (Exception ex) {
				LOG.error("Error occurs when load xml file,source=" + src);
				LOG.error(ExceptionUtils.getStackTrace(ex));
			} finally {
				IOTools.closeStream(in);
			}
			return loader;
		}
	}
	
	/**
	 * 热部署Loader
	 * @author yyduan
	 *
	 * @param <O>
	 */
	public static abstract class Hot<O extends Loadable> extends Loader.Abstract<O> implements Runnable{
		/**
		 * 监听器
		 */
		private List<Watcher<O>> watchers = new ArrayList<Watcher<O>>();
		
		/**
		 * 扫描线程池
		 */
		private ScheduledThreadPoolExecutor threadPool = new ScheduledThreadPoolExecutor(1);
		
		/**
		 * 扫描间隔，缺省:60s
		 */
		private long interval = 60;
		
		/**
		 * 线程启动延迟，缺省:60s
		 */
		private long delay = 60;
		
		/**
		 * 缓存的对象
		 */
		private Map<String,O> objects = new ConcurrentHashMap<String,O>();
		
		/**
		 * 向缓存加入对象
		 * @param id 对象id
		 * @param o 对象
		 */
		protected void add(String id,O o){
			objects.put(id, o);
		}
		
		/**
		 * 从缓存中删除对象
		 * @param id 对象
		 */
		protected void remove(String id){
			objects.remove(id);
		}
		
		/**
		 * 清楚缓存所有对象
		 */
		protected void clear(){
			objects.clear();
		}
		
		/**
		 * 获取缓存中的对象
		 * @param id 对象id
		 * @return 对象实例
		 */
		protected O get(String id){
			return objects.get(id);
		}
		
		/**
		 * 缓存中是否包含指定id的对象
		 * @param id 对象id
		 * @return 是否包含指定id的对象
		 */
		protected boolean contain(String id){
			return objects.containsKey(id);
		}
		
		@Override
		public void addWatcher(Watcher<O> watcher) {
			super.addWatcher(watcher);
			watchers.add(watcher);
		}
		
		@Override
		public void removeWatcher(Watcher<O> watcher) {
			super.removeWatcher(watcher);
			watchers.remove(watcher);
		}
		
		@Override
		public void configure(Properties p) {
			super.configure(p);
			
			//初次加载
			doLoad(true);
			
			//启动扫描线程
			threadPool.scheduleAtFixedRate(this, delay, interval, TimeUnit.SECONDS);
		}
		
		/**
		 * 执行扫描加载过程
		 * @param first 是否第一次加载
		 */
		protected abstract void doLoad(boolean first);

		@Override
		public void run(){
			doLoad(false);
		}

		@Override
		public O load(String id, boolean cacheAllowed) {
			return get(id);
		}
		
		/**
		 * 触发对象增加事件
		 * @param id 对象id
		 * @param o 对象实例
		 */
		protected void fireAdded(String id,O o){
			for (Watcher<O> w:watchers){
				w.added(id, o);
			}
		}
		
		/**
		 * 触发对象被删除事件
		 * @param id 对象id
		 * @param o 对象实例
		 */
		protected void fireRemove(String id,O o){
			for (Watcher<O> w:watchers){
				w.removed(id, o);
			}
		}
		
		/**
		 * 触发对象变更事件
		 * @param id 对象id
		 * @param o 对象实例
		 */
		protected void fireChanged(String id,O o){
			for (Watcher<O> w:watchers){
				w.changed(id, o);
			}
		}
		
		/**
		 * 触发所有对象变更事件
		 */
		protected void fireAllChanged(){
			for (Watcher<O> w:watchers){
				w.allChanged();
			}
		}
	}
	
	/**
	 * 热部署文件
	 * 
	 * @author yyduan
	 *
	 */
	public static abstract class HotFile<O extends Loadable> extends Loader.Abstract<O> implements Runnable{
		protected String digest = "";
		protected String filePath;
		protected ScheduledThreadPoolExecutor threadPool = new ScheduledThreadPoolExecutor(1);
		protected long interval = 10;
		protected long delay = 10;
		
		private Map<String,O> objects = new ConcurrentHashMap<String,O>();

		/**
		 * 监听器
		 */
		private List<Watcher<O>> watchers = new ArrayList<Watcher<O>>();
		
		@Override
		public void configure(Properties p){
			super.configure(p);		
			filePath = PropertiesConstants.getString(p, "path", "");
			interval = PropertiesConstants.getLong(p,"interval",interval);
			delay = PropertiesConstants.getLong(p,"delay",delay);
			
			loadFromPath(objects,filePath);
			
			threadPool.scheduleAtFixedRate(this, delay, interval, TimeUnit.SECONDS);
		}
		
		@Override
		public O load(String id, boolean cacheAllowed) {
			return objects.get(id);
		}
		
		/**
		 * 从指定路径装入信息
		 * 
		 * @param path
		 */
		protected void loadFromPath(final Map<String,O> container,final String path){
			File file = new File(path);
			if (file.exists() && file.isFile()){
				digest = getFileDigest(file);
				loadFromFile(container,file);
			}
		}
		
		protected synchronized void loadFromFile(final Map<String,O> container,final File file){
			InputStream in = null;			
			try {
				in = new FileInputStream(file);
				Document doc = XmlTools.loadFromInputStream(in);
				if (doc != null){
					loadFromElement(container,doc.getDocumentElement(),Settings.get());
				}
			} catch (Exception e) {
				LOG.error("Can not open file : " + file.getPath());
				LOG.error(ExceptionUtils.getStackTrace(e));
			}  finally{
				IOTools.close(in);
			}		
		}

		protected void loadFromElement(final Map<String,O> container,Element root,Properties p){
			NodeList nodeList = XmlTools.getNodeListByPath(root, getObjectXmlTag());
			Factory<O> factory = new Factory<O>();
			
			for (int i = 0 ;i < nodeList.getLength() ; i ++){
				Node n = nodeList.item(i);
				
				if (Node.ELEMENT_NODE != n.getNodeType()){
					continue;
				}
				
				Element e = (Element)n;
				O instance = factory.newInstance(e, p, "module", getObjectDftClass());				
				if (StringUtils.isNotEmpty(instance.getId())){
					container.put(instance.getId(), instance);
				}
			}
		}
		
		protected String getObjectXmlTag() {
			return "model";
		}

		protected abstract String getObjectDftClass();
		
		@Override
		public void addWatcher(Watcher<O> watcher) {
			super.addWatcher(watcher);
			watchers.add(watcher);
		}
		
		@Override
		public void removeWatcher(Watcher<O> watcher) {
			super.removeWatcher(watcher);
			watchers.remove(watcher);
		}
		
		@Override
		public void run() {
			File file = new File(filePath);
			if (file.exists() && file.isFile()){
				String md5 = getFileDigest(file);
				if (md5 != null && ! md5.equals(digest)){
					digest = md5;				
					LOG.info("File has been changed:" + filePath);			
					
					Map<String,O> temp = new ConcurrentHashMap<String,O>();
					loadFromFile(temp,file);				
					objects = temp;				
					for (Watcher<O> w:watchers){
						w.allChanged();
					}
				}
			}
		}
		
		protected synchronized String getFileDigest(File file){
			String digest = null;
			InputStream in;
			try {
				in = new FileInputStream(file);
				digest = DigestUtils.md5Hex(in);				
			} catch (Exception e) {
				LOG.error("Can not open file : " + file.getPath());
				LOG.error(ExceptionUtils.getStackTrace(e));
			}
			return digest;
		}

	}
}
