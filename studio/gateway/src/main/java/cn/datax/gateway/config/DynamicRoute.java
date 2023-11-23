package cn.datax.gateway.config;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author zlinfozhu
 */
@Component
@Slf4j
public class DynamicRoute implements ApplicationEventPublisherAware {
    private final GatewayNacosConfig nacosConfig;

    private final ConfigService configService;

    private final NamingService namingService;

    private final RouteDefinitionWriter writer;

    private ApplicationEventPublisher publisher;

    @Autowired
    public DynamicRoute(GatewayNacosConfig nacosConfig,
                        ConfigService configService,
                        RouteDefinitionWriter writer,
                        NamingService namingService) {
        this.nacosConfig = nacosConfig;
        this.configService = configService;
        this.writer = writer;
        this.namingService = namingService;
    }

    @PostConstruct
    public void init() throws NacosException {
        String dataId = nacosConfig.getDataId();
        String group = nacosConfig.getGroup();

        namingService.registerInstance(nacosConfig.getServerName(),
                nacosConfig.getGroup(), "10.10.188.123", nacosConfig.getPort());
        configService.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String s) {
                refresh(s);
            }
        });
    }

    private void refresh(String s) {
        List<RouteDefinition> routeDefinitions = JSONArray.parseArray(s, RouteDefinition.class);
        for (RouteDefinition routeDefinition : routeDefinitions) {
            try {
                this.writer.delete(Mono.just(routeDefinition.getId())).subscribe();
            } catch (Exception e) {
                log.warn("Update route failed, not find routeId: {}", routeDefinition.getId());
            }

            try {
                this.writer.save(Mono.just(routeDefinition)).subscribe();
            } catch (Exception e) {
                log.error("Update route failed, routeId: {}", routeDefinition.getId());
            }
            this.publisher.publishEvent(new RefreshRoutesEvent(this));
        }

    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }
}
