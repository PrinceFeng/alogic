alogic-1.6.7
============

文档记录了alogic-1.6.7的更新日志。

### 1.6.7.1 [20170116 duanyy]

- alogic-common:公式解析器(Parser)修改方法为protected，增加可定制性;
- alogic-jms:淘汰alogic-jms;
	
### 1.6.7.2 [20170117 duanyy]
	 
- alogic-commom:trace日志调用链中的调用次序采用xx.xx.xx.xx字符串模式;

### 1.6.7.3 [20170118 duanyy]
- alogic-common:新增com.alogic.tlog，替代com.alogic.tracer.log包;
- alogic-common:trace日志的时长单位改为ns;
- alogic-common:对tlog的开启开关进行了统一;

### 1.6.7.4 [20170118 duanyy]
- alogic-common:增加发送指标所需的xscript插件;
- alogic-common:淘汰com.anysoft.metrics包;
- alogic-core:服务耗时统计修改为ns;

### 1.6.7.5 [20170119 duanyy]
- alogic-common:JsonTools允许输入的json为空;
- alogic-vfs:增加VFS相关的XScript插件；

### 1.6.7.6 [20170125 duanyy] 
- alogic-common:Batch框架可以装入额外的CLASSPATH;