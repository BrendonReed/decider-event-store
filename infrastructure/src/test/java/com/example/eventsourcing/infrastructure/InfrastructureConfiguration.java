package com.example.eventsourcing.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@SpringBootApplication(scanBasePackages = {"decider.event.store", "com.example.eventsourcing.infrastructure"})
@EnableTransactionManagement
public class InfrastructureConfiguration {}
