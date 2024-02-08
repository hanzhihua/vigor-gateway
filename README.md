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

提供http协议

#### sink

提供kafka sink


