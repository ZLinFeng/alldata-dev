package cn.datax.gateway.config;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.nacos.api.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zlinfozhu
 */
@Component
@Slf4j
public class NacosRouteDefinitionRepository implements RouteDefinitionRepository {

    private final ConfigService configService;

    private final GatewayNacosConfig nacosConfig;

    @Autowired
    public NacosRouteDefinitionRepository(ConfigService configService,
                                          GatewayNacosConfig nacosConfig) {
        this.configService = configService;
        this.nacosConfig = nacosConfig;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        List<RouteDefinition> routeDefinitions = new ArrayList<>();
        try {
            String s = configService.getConfig(nacosConfig.getDataId(), nacosConfig.getGroup(), 1000L);
            routeDefinitions = JSONArray.parseArray(s, RouteDefinition.class);
        } catch (Exception e) {
            log.error("Cannot find nacos routes config, dataId[{}]", nacosConfig.getDataId());
        }
        return Flux.fromIterable(routeDefinitions);
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.empty();
    }
}
