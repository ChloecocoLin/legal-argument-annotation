package com.argument.automation;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
@Configuration @EnableScheduling @Profile("local & !test & !prod")
@ConditionalOnProperty(name="member-d.automation.test-worker-enabled",havingValue="true",matchIfMissing=true)
public class AutomationScheduler {
    private final AutomationWorker worker;
    public AutomationScheduler(AutomationWorker worker){this.worker=worker;}
    @Scheduled(fixedDelay=3000) public void tick(){try{worker.processOne();}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Automation transaction rolled back; retained state will be retried or expired");}}
}
