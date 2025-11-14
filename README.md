# Modbus 设备监控系统

基于 Spring Boot 3 和 Apache PLC4X 实现的 Modbus 设备监控和控制系统，支持 Modbus 和 S7 协议模拟器。

## 功能特性

### 核心功能
- 监控设备 A 的标志位状态
- 检测状态变化（从 0 到 1）
- 自动触发设备 B 的标志位设置
- 自动重连机制
- 可配置的轮询间隔

### 模拟器功能
- **Modbus TCP 模拟服务器**：完整的 Modbus TCP 从站实现
  - 支持线圈（Coils）、离散输入（Discrete Inputs）
  - 支持保持寄存器（Holding Registers）、输入寄存器（Input Registers）
  - 支持读/写功能码
  - 可配置多个模拟设备

- **S7 协议模拟服务器**：S7 通信模拟
  - 支持基本的 S7 连接和数据交换
  - 支持数据块（DB）、标志位（Merker）
  - 适用于测试和开发

## 技术栈

- Java 21
- Spring Boot 3.2.0
- Apache PLC4X 0.12.0（支持 Modbus 和 S7）
- Digitalpetri Modbus 1.2.0（Modbus 服务器）
- Netty 4.1.100（网络通信）
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

## 模拟器配置

系统内置了 Modbus 和 S7 协议模拟服务器，方便在没有真实设备的情况下进行测试和开发。

### 启用模拟器

在 `application.yml` 中配置：

```yaml
simulator:
  # 是否启用模拟器
  enabled: true

  # Modbus 模拟服务器列表
  modbus-servers:
    - name: "设备A模拟器"
      port: 5020
      unit-id: 1
      enabled: true

    - name: "设备B模拟器"
      port: 5021
      unit-id: 1
      enabled: true

  # S7 模拟服务器列表
  s7-servers:
    - name: "S7设备模拟器"
      port: 1102
      rack: 0
      slot: 0
      enabled: true
```

### 使用模拟器进行测试

1. **启用模拟器**：设置 `simulator.enabled: true`

2. **配置客户端连接到模拟器**：
```yaml
modbus:
  device-a:
    host: localhost  # 或 127.0.0.1
    port: 5020       # 模拟器端口
    unit-id: 1
    flag-address: "coil:0"

  device-b:
    host: localhost
    port: 5021
    unit-id: 1
    flag-address: "coil:0"
```

3. **启动应用**：模拟器会自动启动并监听配置的端口

4. **验证模拟器运行**：查看日志输出
```
===== 启动模拟器服务 =====
✓ Modbus 服务器 '设备A模拟器' 启动成功
✓ Modbus 服务器 '设备B模拟器' 启动成功
✓ S7 服务器 'S7设备模拟器' 启动成功
===== 模拟器服务启动完成 =====
```

### 模拟器支持的功能

**Modbus 模拟器**：
- 读线圈（Function Code 0x01）
- 读离散输入（Function Code 0x02）
- 读保持寄存器（Function Code 0x03）
- 读输入寄存器（Function Code 0x04）
- 写单个线圈（Function Code 0x05）
- 写单个寄存器（Function Code 0x06）
- 写多个线圈（Function Code 0x0F）
- 写多个寄存器（Function Code 0x10）

**S7 模拟器**：
- 基本的 COTP 连接建立
- 数据读写模拟
- 支持数据块和标志位

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
│   ├── ModbusProperties.java          # Modbus 配置属性类
│   └── SimulatorProperties.java       # 模拟器配置属性类
├── service/
│   ├── ModbusConnectionManager.java   # 连接管理器
│   ├── DeviceService.java             # 设备读写服务
│   └── MonitorService.java            # 监控服务
└── simulator/
    ├── ModbusSimulatorServer.java     # Modbus 模拟服务器
    ├── S7SimulatorServer.java         # S7 模拟服务器
    └── SimulatorManager.java          # 模拟器管理器
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
