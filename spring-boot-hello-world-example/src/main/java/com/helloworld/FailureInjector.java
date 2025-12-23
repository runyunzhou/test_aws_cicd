package com.helloworld;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 可配置的部署故障注入器。
 * 在应用启动时检查 classpath 下是否存在 /fail_deploy.flag 文件，
 * 如果存在则抛出异常终止启动（用于测试 CodeDeploy/ALB/CloudWatch 告警）。
 *
 * 触发方法：在项目中添加空文件 `src/main/resources/fail_deploy.flag`，重新构建并部署。
 */
@Component
public class FailureInjector implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        InputStream flagStream = getClass().getResourceAsStream("/fail_deploy.flag");
        if (flagStream != null) {
            // 发现触发文件，主动终止应用启动以模拟部署失败
            flagStream.close();
            throw new IllegalStateException("Injected deployment failure: fail_deploy.flag present on classpath");
        }
    }
}


