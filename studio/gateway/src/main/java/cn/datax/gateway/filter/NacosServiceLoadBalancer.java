package cn.datax.gateway.filter;

import cn.datax.gateway.config.GatewayNacosConfig;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * @author zlinfozhu
 */
@Component
@Slf4j
public class NacosServiceLoadBalancer implements GlobalFilter, Ordered {
    Map<String, CopyOnWriteArrayList<String>> map = new HashMap<>();

    private final NamingService namingService;

    private final GatewayNacosConfig gatewayNacosConfig;

    private final ConfigService configService;

    @Autowired
    public NacosServiceLoadBalancer(NamingService namingService,
                                    GatewayNacosConfig gatewayNacosConfig,
                                    ConfigService configService) {
        this.namingService = namingService;
        this.gatewayNacosConfig = gatewayNacosConfig;
        this.configService = configService;
    }

    @PostConstruct
    public void init() throws Exception {
        String config = configService.getConfig(gatewayNacosConfig.getDataId(),
                gatewayNacosConfig.getGroup(),
                1000L);
        refreshInstances(config);
        configService.addListener(gatewayNacosConfig.getDataId(),
                gatewayNacosConfig.getGroup(),
                new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String s) {
                        refreshInstances(s);
                    }
                });
    }

    public void refreshInstances(String s) {
        try {
            List<RouteDefinition> routeDefinitions = JSONObject.parseArray(s, RouteDefinition.class);
            for (RouteDefinition definition : routeDefinitions) {
                String schema = definition.getUri().getScheme();
                if ("http".equals(schema) || "https".equals(schema)) {
                    continue;
                }
                String serviceName = definition.getUri().getHost();
                if (!map.containsKey(serviceName)) {
                    map.put(serviceName, new CopyOnWriteArrayList<>());
                }
                List<Instance> allInstances = namingService.getAllInstances(serviceName);
                for (Instance instance : allInstances) {
                    if (!instance.isHealthy()) {
                        continue;
                    }
                    String newAddr = "http://" + instance.getIp() + ":" + instance.getPort();
                    map.get(serviceName).add(newAddr);
                }
            }
        } catch (Exception e) {
            log.error("Update service address failed.", e);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI url = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        if (null == url) {
            return chain.filter(exchange);
        }
        String host = url.getHost();
        String schema = url.getScheme();

        if ("http".equals(schema)) {
            return chain.filter(exchange);
        }
        List<String> hosts = map.get(host);
        Random random = new Random();
        String newUrl = hosts.get(random.nextInt(hosts.size())) + url.getPath();

        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,
                UriComponentsBuilder.fromHttpUrl(newUrl).build().toUri());

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }
}
