package com.argument.teachingtrial;
import java.time.*;
import org.springframework.stereotype.Component;
@Component public class TeachingTime {public Instant now(){return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);}}
