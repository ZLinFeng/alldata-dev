package cn.datax.gateway.config;

import cn.datax.gateway.GatewayApplicationTest;
import com.alibaba.nacos.api.config.ConfigService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class NacosConfigTest extends GatewayApplicationTest {

    @Autowired
    private ConfigService configService;

    @Test
    public void read() {
        System.out.println(configService);
    }
}
