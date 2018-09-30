package com.alogic.together2.service;

import org.apache.commons.lang3.StringUtils;

import com.alogic.together.service.SevantLogicletContext;
import com.alogic.together2.TogetherServiceDescription;
import com.alogic.xscript.LogicletContext;
import com.alogic.xscript.Script;
import com.alogic.xscript.doc.XsObject;
import com.alogic.xscript.doc.json.JsonObject;
import com.alogic.xscript.doc.xml.XmlObject;
import com.anysoft.util.Properties;
import com.anysoft.util.PropertiesConstants;
import com.logicbus.backend.AbstractServant;
import com.logicbus.backend.Context;
import com.logicbus.backend.message.JsonMessage;
import com.logicbus.backend.message.XMLMessage;
import com.logicbus.models.servant.ServiceDescription;

/**
 * 专用Servant
 * @author yyduan
 * 
 * @since 1.6.11.3
 * 
 * @version 1.6.11.22 [duanyy 20180314] <br>
 * - 支持按指定jsonpath路径来输出文档 <br>
 * 
 * @version 1.6.11.59 [20180911 duanyy] <br>
 * - 使用新的ServantLogicletContext类;
 */
public class TogetherServant extends AbstractServant {
	protected Script script = null;
	protected String service;
	
	@Override
	protected void onDestroy() {
		
	}

	@Override
	protected void onCreate(ServiceDescription sd) {
		service = sd.getPath();
		if (sd instanceof TogetherServiceDescription){
			script = ((TogetherServiceDescription)sd).getScript();
		}else{
			Properties p = sd.getProperties();
			String bootstrap = PropertiesConstants.getString(p,"bootstrap","",true);
			if (StringUtils.isEmpty(bootstrap)){
				String config = PropertiesConstants.getString(p,"script","");
				if (StringUtils.isNotEmpty(config)){
					script = Script.create(config, p);
				}
			}else{
				String config = PropertiesConstants.getString(p,"script","");
				if (StringUtils.isNotEmpty(config)){
					script = Script.create(bootstrap, config, p);
				}
			}
		}
	}

	@Override
	protected int onJson(Context ctx)  {
		if (script != null){
			JsonMessage msg = (JsonMessage) ctx.asMessage(JsonMessage.class);
			
			LogicletContext logicletContext = new Context.ServantLogicletContext(ctx);
			logicletContext.setObject("$context", ctx);
			logicletContext.SetValue("$service", service);
			try {
				XsObject doc = new JsonObject("root",msg.getRoot());
				script.execute(doc,doc, logicletContext, null);
			}finally{
				logicletContext.removeObject("$context");
				
				String keyword = logicletContext.GetValue("$keyword", "");
				if (StringUtils.isNotEmpty(keyword)){
					ctx.setKeyword(keyword);
				}			
				
				String outputPath = logicletContext.GetValue("$outpart", "");
				if (StringUtils.isNotEmpty(outputPath)){
					msg.setOutputPath(outputPath);
				}
			}
		}else{
			ctx.asMessage(JsonMessage.class);
		}
		return 0;
	}

	protected int onXml(Context ctx) { 
		if (script != null){
			XMLMessage msg = (XMLMessage) ctx.asMessage(XMLMessage.class);
			
			LogicletContext logicletContext = new SevantLogicletContext(ctx);
			logicletContext.setObject("$context", ctx);
			logicletContext.SetValue("$service", service);
			try {
				XsObject doc = new XmlObject("root",msg.getRoot());
				script.execute(doc,doc, logicletContext, null);
			}finally{
				logicletContext.removeObject("$context");
				
				String keyword = logicletContext.GetValue("$keyword", "");
				if (StringUtils.isNotEmpty(keyword)){
					ctx.setKeyword(keyword);
				}
			}
		}else{
			ctx.asMessage(XMLMessage.class);
		}
		return 0;	
	}
}