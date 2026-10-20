package com.argument.integration;
import com.argument.version.local.LocalFixtureLoader;
import com.argument.version.port.AnnotationDependencies;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Component @Profile("(local | test) & !prod") @Order(20)
public class LocalAccountLoader implements ApplicationRunner {
 private final AnnotationDependencies data;private final JdbcTemplate jdbc;private final PasswordEncoder passwords;
 public LocalAccountLoader(AnnotationDependencies data,JdbcTemplate jdbc,PasswordEncoder passwords){this.data=data;this.jdbc=jdbc;this.passwords=passwords;}
 @Transactional public void run(ApplicationArguments args){String[] names={"admin.wang","creator.li","annotator.chen","annotator.liu","arbiter.zhao","teacher.sun","student.zhou","expert.qian"};String[] ids={"bca3772c-2ec2-4001-9521-9f825c33a3f2","9795b546-9838-4ef5-ab05-4ba73d88ea7e","1068a77e-2a72-4f38-ae97-dcb88a45c7b3","17e46666-aec6-4ed2-8e48-c044014f94a1","9e76d32b-86e7-4d62-84e1-7f60e7c0b502","2901c453-e8c4-4cfa-948c-7dedb4f2ab8a","52b8374c-4ad4-4aca-bed8-1b7bc6687577","4809fe74-569e-4906-a453-423c290b0525"};for(int i=0;i<ids.length;i++){if(jdbc.queryForObject("SELECT COUNT(*) FROM auth_accounts WHERE user_id=?",Integer.class,ids[i])==0){try{data.read("user",ids[i]);}catch(com.argument.common.exception.BusinessException e){continue;}jdbc.update("INSERT INTO auth_accounts(user_id,username,password_hash) VALUES(?,?,?)",ids[i],names[i],passwords.encode("demo"));}}}
}
