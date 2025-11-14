package com.example.modbus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Modbus设备监控应用主类
 *
 * 功能：监控设备A的标志位，当值从0变成1时，将设备B的标志位设置为1
 */
@SpringBootApplication
@EnableScheduling
public class ModbusMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModbusMonitorApplication.class, args);
    }
}
