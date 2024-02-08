## vigor:

大数据工具平台

### vigor-gateway 

数据网关，数据通过它入湖入仓

#### 数据格式

* pb格式
<code>
message SimpleEvent {
string logId = 1;
map<string, string> meta = 3;
bytes data = 4;
int64 ctime = 5;
}
</code>

* logId
作为日志标识

* 移动端遵循日志spm规范

#### source

提供http协议, 
* header需要
* body里面放入SimpleEvent的二进制数组

#### sink

提供kafka sink，集群粒度隔离

每个集群包含下列三个组件：

* 多个kafka producer,
* 多个发送任务，每个发送任务随机分配一个kafka producer
* 待发送消息列表，map结构，key为logid,value:消息列表


