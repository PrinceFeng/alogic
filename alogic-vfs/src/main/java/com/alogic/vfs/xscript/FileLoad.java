package com.alogic.vfs.xscript;

import java.io.IOException;
import java.io.InputStream;
import com.alogic.vfs.core.VirtualFileSystem;
import com.alogic.xscript.AbstractLogiclet;
import com.alogic.xscript.ExecuteWatcher;
import com.alogic.xscript.Logiclet;
import com.alogic.xscript.LogicletContext;
import com.alogic.xscript.doc.XsObject;
import com.anysoft.util.BaseException;
import com.anysoft.util.Properties;
import com.anysoft.util.PropertiesConstants;

/**
 * 装入指定文件的内容
 * @author duanyy
 * @since 1.6.7.8
 * 
 * @version 1.6.9.1 [20170516 duanyy] <br>
 * - 修复部分插件由于使用新的文档模型产生的兼容性问题 <br>
 */
public class FileLoad extends AbstractLogiclet{
	protected String pid = "$vfs";
	protected String path = "/newfile";
	protected String id = "$vfs-load";
	protected String encoding = "utf-8";
	protected String dft = "";
	protected int contentLength = 1024;
	
	public FileLoad(String tag, Logiclet p) {
		super(tag, p);
	}
	
	@Override
	public void configure(Properties p){
		super.configure(p);
		
		pid = PropertiesConstants.getString(p,"pid",pid,true);
		id = PropertiesConstants.getString(p,"id",id,true);
		path = PropertiesConstants.getRaw(p,"path",path);
		encoding = PropertiesConstants.getString(p,"encoding",encoding,true);
		dft = PropertiesConstants.getRaw(p, "dft", dft);
		contentLength = PropertiesConstants.getInt(p,"contentLength",contentLength);
		contentLength = contentLength <= 0? 1024:contentLength;
	}

	@Override
	protected void onExecute(XsObject root,XsObject current, LogicletContext ctx,
			ExecuteWatcher watcher) {
		VirtualFileSystem vfs = ctx.getObject(pid);
		if (vfs == null){
			throw new BaseException("core.e1001",String.format("Can not find vfs:%s", pid));
		}
		
		String pathValue = ctx.transform(path);
		
		InputStream in = vfs.readFile(pathValue);
		if (in == null){
			ctx.SetValue(id, ctx.transform(dft));
		}else{
			try {
				byte[] content = new byte[contentLength];
				int realLength = in.read(content);
				ctx.SetValue(id, new String(content,0,realLength,encoding));
			}catch (IOException e){
				throw new BaseException("core.io_exception",
						String.format("Can not read file %s:%s", pathValue,e.getMessage()));
			}finally{
				vfs.finishRead(pathValue, in);
			}
		}
	}
}
