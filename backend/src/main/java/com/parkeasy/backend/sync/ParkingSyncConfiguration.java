package com.parkeasy.backend.sync;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ParkingSyncProperties.class)
public class ParkingSyncConfiguration { }
