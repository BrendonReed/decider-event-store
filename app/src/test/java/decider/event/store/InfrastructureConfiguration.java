package decider.event.store;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@SpringBootApplication(scanBasePackages = {"decider.event.store", "shared"})
@EnableTransactionManagement
public class InfrastructureConfiguration {}
