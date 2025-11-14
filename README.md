# Modbus 设备监控系统

基于 Spring Boot 3 和 Apache PLC4X 实现的 Modbus 设备监控和控制系统。

## 功能特性

- 监控设备 A 的标志位状态
- 检测状态变化（从 0 到 1）
- 自动触发设备 B 的标志位设置
- 自动重连机制
- 可配置的轮询间隔

## 技术栈

- Java 21
- Spring Boot 3.2.0
- Apache PLC4X 0.12.0
- Maven
- Lombok

## 系统要求

- JDK 21 或更高版本
- Maven 3.6+
- 支持 Modbus TCP 协议的设备

## 配置说明

编辑 `src/main/resources/application.yml` 文件配置设备参数：

```yaml
modbus:
  # 设备 A 配置（被监控设备）
  device-a:
    host: 192.168.1.100        # 设备 A 的 IP 地址
    port: 502                   # Modbus 端口
    unit-id: 1                  # Modbus 单元 ID
    flag-address: "coil:0"      # 标志位地址

  # 设备 B 配置（被控制设备）
  device-b:
    host: 192.168.1.101        # 设备 B 的 IP 地址
    port: 502
    unit-id: 1
    flag-address: "coil:0"

  # 监控配置
  monitor:
    poll-interval-ms: 1000      # 轮询间隔（毫秒）
    connection-timeout-ms: 5000 # 连接超时
    request-timeout-ms: 3000    # 请求超时
```

### 地址格式说明

标志位地址支持以下格式：

- `coil:地址` - 线圈（读写，0x01/0x05）
- `discrete-input:地址` - 离散输入（只读，0x02）
- `input-register:地址` - 输入寄存器（只读，0x04）
- `holding-register:地址` - 保持寄存器（读写，0x03/0x06）

示例：
- `coil:0` - 线圈地址 0
- `holding-register:100` - 保持寄存器地址 100

## 构建和运行

### 构建项目

```bash
mvn clean package
```

### 运行应用

```bash
mvn spring-boot:run
```

或者运行编译后的 JAR：

```bash
java -jar target/modbus-monitor-1.0.0.jar
```

## 工作原理

1. **初始化阶段**：应用启动时建立与设备 A 和设备 B 的 Modbus 连接
2. **监控阶段**：按配置的轮询间隔定期读取设备 A 的标志位
3. **检测阶段**：比较当前状态与上一次状态，检测从 0 到 1 的变化
4. **触发阶段**：当检测到状态变化时，立即将设备 B 的标志位设置为 1
5. **重连机制**：如果连接失败，系统会自动尝试重新连接

## 项目结构

```
src/main/java/com/example/modbus/
├── ModbusMonitorApplication.java      # 主应用类
├── config/
│   └── ModbusProperties.java          # 配置属性类
└── service/
    ├── ModbusConnectionManager.java   # 连接管理器
    ├── DeviceService.java             # 设备读写服务
    └── MonitorService.java            # 监控服务
```

## 日志说明

应用会输出详细的日志信息：

- `DEBUG` 级别：记录每次读取操作
- `INFO` 级别：记录状态变化和写入操作
- `ERROR` 级别：记录连接错误和操作失败

可以在 `application.yml` 中调整日志级别：

```yaml
logging:
  level:
    com.example.modbus: DEBUG  # 应用日志级别
    org.apache.plc4x: INFO     # PLC4X 库日志级别
```

## 故障排查

### 连接失败

- 检查设备 IP 地址和端口是否正确
- 确认设备支持 Modbus TCP 协议
- 检查防火墙设置
- 验证网络连通性

### 读写失败

- 检查 unit-id 是否正确
- 验证地址格式是否正确
- 确认设备支持指定的功能码

## 许可证

Apache License 2.0

## 相关资源

- [Apache PLC4X 官方文档](https://plc4x.apache.org/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [Modbus 协议规范](https://www.modbus.org/)
