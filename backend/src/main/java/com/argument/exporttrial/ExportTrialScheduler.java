package com.argument.exporttrial;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
@Configuration @EnableScheduling @Profile("local & !prod & !test")
@ConditionalOnProperty(name="member-d.exports.worker-enabled",havingValue="true",matchIfMissing=true)
public class ExportTrialScheduler {
    private final ExportTrialWorker worker;public ExportTrialScheduler(ExportTrialWorker worker){this.worker=worker;}
    @Scheduled(fixedDelay=3000) public void tick(){try{worker.processOne();}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Export worker transaction rolled back; queued export retained for retry");}}
}
