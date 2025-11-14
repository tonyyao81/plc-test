package com.example.modbus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟器配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    /**
     * 是否启用模拟器
     */
    private boolean enabled = false;

    /**
     * Modbus 模拟服务器列表
     */
    private List<ModbusServerConfig> modbusServers = new ArrayList<>();

    /**
     * S7 模拟服务器列表
     */
    private List<S7ServerConfig> s7Servers = new ArrayList<>();

    @Data
    public static class ModbusServerConfig {
        /**
         * 服务器名称
         */
        private String name;

        /**
         * 监听端口
         */
        private int port = 502;

        /**
         * 单元 ID
         */
        private int unitId = 1;

        /**
         * 是否启用
         */
        private boolean enabled = true;
    }

    @Data
    public static class S7ServerConfig {
        /**
         * 服务器名称
         */
        private String name;

        /**
         * 监听端口
         */
        private int port = 102;

        /**
         * Rack 号
         */
        private int rack = 0;

        /**
         * Slot 号
         */
        private int slot = 0;

        /**
         * 是否启用
         */
        private boolean enabled = true;
    }
}
